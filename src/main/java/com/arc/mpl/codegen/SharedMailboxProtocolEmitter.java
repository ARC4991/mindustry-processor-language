package com.arc.mpl.codegen;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedMailboxLayout;
import com.arc.mpl.memory.SharedRuntimeLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.ALWAYS;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.EQUAL;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.LESS_THAN;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.NOT_EQUAL;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.STRICT_EQUAL;

/** Emits fixed-width, single-slot SPSC mailbox operations for compiler-generated Runtime helpers. */
final class SharedMailboxProtocolEmitter {
    private static final String MAX_COMMITTED_VERSION = "2000000000";
    private final MlogProgramBuilder output;
    private final PhysicalMemoryLayout memoryLayout;
    private final MlogRuntimeContext context;
    private final SharedRuntimeLayout shared;
    private int temporaryIndex;

    SharedMailboxProtocolEmitter(MlogProgramBuilder output, PhysicalMemoryLayout memoryLayout,
                                 MlogRuntimeContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.memoryLayout = Objects.requireNonNull(memoryLayout, "memoryLayout");
        this.context = Objects.requireNonNull(context, "context");
        this.shared = context.sharedRuntime().orElseThrow(() ->
            new IllegalArgumentException("共享邮箱代码生成需要多处理器 Runtime 上下文"));
    }

    /** Waits for an empty slot, then commits kind and payload with an odd/even version transition. */
    String emitSend(String mailboxId, String kind, List<String> payload) {
        return emitSend(mailboxId, kind, payload, () -> { });
    }

    /** Emits the idle hook on every retry while waiting for producer ownership of the slot. */
    String emitSend(String mailboxId, String kind, List<String> payload, Runnable idleHook) {
        SharedMailboxLayout mailbox = producerMailbox(mailboxId);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(idleHook, "idleHook");
        payload = List.copyOf(Objects.requireNonNull(payload, "payload"));
        if (kind.isBlank() || "null".equals(kind)
            || payload.stream().anyMatch(value -> value == null || value.isBlank() || "null".equals(value))) {
            throw new IllegalArgumentException("共享邮箱字段不能使用 null；null 是无效 read 的私有哨兵");
        }
        if (payload.size() != mailbox.payloadSlots()) {
            throw new IllegalArgumentException("共享邮箱 payload 宽度不匹配：" + mailboxId);
        }

        MlogProgramBuilder.Label wait = label("mailbox_send_wait");
        output.label(wait);
        idleHook.run();
        String version = readConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, version, "null");
        String acknowledged = readConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, acknowledged, "null");
        output.jump(wait, NOT_EQUAL, version, acknowledged);
        output.jump(wait, LESS_THAN, version, "0");
        String parity = temporary();
        output.operation(MlogProgramBuilder.Operation.MOD, parity, version, "2");
        output.jump(wait, NOT_EQUAL, parity, "0");

        String committed = temporary();
        MlogProgramBuilder.Label increment = label("mailbox_version_increment");
        MlogProgramBuilder.Label selected = label("mailbox_version_selected");
        output.jump(increment, LESS_THAN, version, MAX_COMMITTED_VERSION);
        output.set(committed, "2");
        output.jump(selected, ALWAYS, "0", "0");
        output.label(increment);
        output.operation(MlogProgramBuilder.Operation.ADD, committed, version, "2");
        output.label(selected);
        String writing = temporary();
        output.operation(MlogProgramBuilder.Operation.SUB, writing, committed, "1");
        writeConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX, writing);
        writeConstant(mailbox.allocation(), SharedMailboxLayout.KIND_INDEX, kind);
        for (int index = 0; index < payload.size(); index++) {
            writeConstant(mailbox.allocation(), mailbox.payloadIndex(index), payload.get(index));
        }
        writeConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX, committed);
        return committed;
    }

    /** Waits for one stable committed message, acknowledges it, and returns compiler-private value variables. */
    ReceivedMessage emitReceive(String mailboxId) {
        return emitReceive(mailboxId, () -> { });
    }

    /** Emits the idle hook on every retry while waiting for a stable committed message. */
    ReceivedMessage emitReceive(String mailboxId, Runnable idleHook) {
        SharedMailboxLayout mailbox = consumerMailbox(mailboxId);
        Objects.requireNonNull(idleHook, "idleHook");
        MlogProgramBuilder.Label wait = label("mailbox_receive_wait");
        output.label(wait);
        idleHook.run();
        String before = readConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, before, "null");
        output.jump(wait, EQUAL, before, "0");
        String parity = temporary();
        output.operation(MlogProgramBuilder.Operation.MOD, parity, before, "2");
        output.jump(wait, NOT_EQUAL, parity, "0");
        String acknowledged = readConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, acknowledged, "null");
        output.jump(wait, EQUAL, before, acknowledged);

        String kind = readRequiredField(wait, mailbox.allocation(), SharedMailboxLayout.KIND_INDEX);
        List<String> payload = new ArrayList<>();
        for (int index = 0; index < mailbox.payloadSlots(); index++) {
            payload.add(readRequiredField(wait, mailbox.allocation(), mailbox.payloadIndex(index)));
        }
        String after = readConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, after, "null");
        output.jump(wait, NOT_EQUAL, before, after);
        writeConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX, after);
        String confirmed = readConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, confirmed, "null");
        output.jump(wait, NOT_EQUAL, confirmed, after);
        return new ReceivedMessage(after, kind, payload);
    }

    /** Waits until the consumer has durably acknowledged the exact committed version. */
    void emitAwaitAcknowledged(String mailboxId, String committedVersion) {
        emitAwaitAcknowledged(mailboxId, committedVersion, () -> { });
    }

    /** Emits the idle hook on every acknowledgement retry. */
    void emitAwaitAcknowledged(String mailboxId, String committedVersion, Runnable idleHook) {
        SharedMailboxLayout mailbox = producerMailbox(mailboxId);
        if (committedVersion == null || committedVersion.isBlank() || "null".equals(committedVersion)) {
            throw new IllegalArgumentException("共享邮箱提交版本不能使用 null 或空值");
        }
        Objects.requireNonNull(idleHook, "idleHook");
        MlogProgramBuilder.Label wait = label("mailbox_acknowledgement_wait");
        output.label(wait);
        idleHook.run();
        String acknowledged = readConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX);
        output.jump(wait, STRICT_EQUAL, acknowledged, "null");
        output.jump(wait, NOT_EQUAL, acknowledged, committedVersion);
    }

    private String readRequiredField(MlogProgramBuilder.Label wait, PhysicalMemoryLayout.Allocation allocation,
                                     int index) {
        String value = readConstant(allocation, index);
        output.jump(wait, STRICT_EQUAL, value, "null");
        return value;
    }

    private SharedMailboxLayout producerMailbox(String id) {
        SharedMailboxLayout mailbox = shared.mailbox(id);
        if (!mailbox.producer().equals(context.shardId())) {
            throw new IllegalArgumentException("当前 shard 不是共享邮箱生产者：" + id);
        }
        return mailbox;
    }

    private SharedMailboxLayout consumerMailbox(String id) {
        SharedMailboxLayout mailbox = shared.mailbox(id);
        if (!mailbox.consumer().equals(context.shardId())) {
            throw new IllegalArgumentException("当前 shard 不是共享邮箱消费者：" + id);
        }
        return mailbox;
    }

    /** Every field is reset because an invalid v146 Memory read can retain the previous destination. */
    private String readConstant(PhysicalMemoryLayout.Allocation allocation, int index) {
        PhysicalMemoryLayout.Slice slice = constantSlice(allocation, index);
        String result = temporary();
        output.set(result, "null");
        output.read(result, segment(slice).alias(),
            Integer.toString(slice.offset() + index - slice.logicalStart()));
        return result;
    }

    private void writeConstant(PhysicalMemoryLayout.Allocation allocation, int index, String value) {
        PhysicalMemoryLayout.Slice slice = constantSlice(allocation, index);
        output.write(value, segment(slice).alias(),
            Integer.toString(slice.offset() + index - slice.logicalStart()));
    }

    private PhysicalMemoryLayout.Slice constantSlice(PhysicalMemoryLayout.Allocation allocation, int index) {
        return allocation.slices().stream().filter(slice -> slice.contains(index)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("共享邮箱下标超出物理分配：" + index));
    }

    private PhysicalMemoryLayout.Segment segment(PhysicalMemoryLayout.Slice slice) {
        return memoryLayout.segments().get(slice.segmentIndex());
    }

    private MlogProgramBuilder.Label label(String role) {
        return output.newLabel(role);
    }

    private String temporary() {
        return "__mpl_mailbox" + temporaryIndex++;
    }

    record ReceivedMessage(String version, String kind, List<String> payload) {
        ReceivedMessage {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(kind, "kind");
            payload = List.copyOf(Objects.requireNonNull(payload, "payload"));
        }
    }
}
