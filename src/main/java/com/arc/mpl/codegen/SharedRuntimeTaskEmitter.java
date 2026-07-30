package com.arc.mpl.codegen;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedRuntimeLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.ALWAYS;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.EQUAL;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.GREATER_THAN_EQ;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.STRICT_EQUAL;

/** Emits the compiler-private Worker task loop on top of paired SPSC mailboxes. */
final class SharedRuntimeTaskEmitter {
    static final int SHUTDOWN_KIND = 0;
    private static final String MAX_HEARTBEAT = "2000000000";

    private final MlogProgramBuilder output;
    private final PhysicalMemoryLayout memoryLayout;
    private final MlogRuntimeContext context;
    private final SharedRuntimeLayout shared;
    private final SharedMailboxProtocolEmitter mailboxes;
    private int temporaryIndex;

    SharedRuntimeTaskEmitter(MlogProgramBuilder output, PhysicalMemoryLayout memoryLayout,
                             MlogRuntimeContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.memoryLayout = Objects.requireNonNull(memoryLayout, "memoryLayout");
        this.context = Objects.requireNonNull(context, "context");
        this.shared = context.sharedRuntime().orElseThrow(() ->
            new IllegalArgumentException("共享任务调度需要多处理器 Runtime 上下文"));
        this.mailboxes = new SharedMailboxProtocolEmitter(output, memoryLayout, context);
    }

    /** Polls requests forever, dispatches statically known kinds, and stops only after shutdown is consumed. */
    void emitWorkerLoop(String requestMailboxId, String responseMailboxId, List<TaskHandler> handlers) {
        if (!context.worker()) throw new IllegalArgumentException("只有 Worker shard 可以生成任务轮询循环");
        handlers = validateHandlers(handlers);

        MlogProgramBuilder.Label poll = output.newLabel("runtime_task_poll");
        MlogProgramBuilder.Label shutdown = output.newLabel("runtime_task_shutdown");
        List<MlogProgramBuilder.Label> entries = handlers.stream()
            .map(ignored -> output.newLabel("runtime_task_handler")).toList();

        output.label(poll);
        SharedMailboxProtocolEmitter.ReceivedMessage request = mailboxes.emitReceive(
            requestMailboxId, this::emitHeartbeat);
        output.jump(shutdown, EQUAL, request.kind(), Integer.toString(SHUTDOWN_KIND));
        for (int index = 0; index < handlers.size(); index++) {
            output.jump(entries.get(index), EQUAL, request.kind(), Integer.toString(handlers.get(index).kind()));
        }
        // A valid compiler-generated deployment cannot publish an unknown kind. Ignore stale/corrupt input safely.
        output.jump(poll, ALWAYS, "0", "0");

        for (int index = 0; index < handlers.size(); index++) {
            output.label(entries.get(index));
            List<String> response = handlers.get(index).body().emit(request.payload());
            mailboxes.emitSend(responseMailboxId, request.kind(), response, this::emitHeartbeat);
            output.jump(poll, ALWAYS, "0", "0");
        }
        output.label(shutdown);
        output.stop();
    }

    /** Publishes shutdown and waits until the Worker has consumed it before Main may stop. */
    void emitShutdownAndAwait(String requestMailboxId) {
        if (!context.main()) throw new IllegalArgumentException("只有 Main shard 可以发起 Worker shutdown");
        int payloadSlots = shared.mailbox(requestMailboxId).payloadSlots();
        List<String> emptyPayload = new ArrayList<>(payloadSlots);
        for (int index = 0; index < payloadSlots; index++) emptyPayload.add("0");
        String committed = mailboxes.emitSend(requestMailboxId, Integer.toString(SHUTDOWN_KIND), emptyPayload);
        mailboxes.emitAwaitAcknowledged(requestMailboxId, committed);
    }

    private List<TaskHandler> validateHandlers(List<TaskHandler> handlers) {
        handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
        if (handlers.isEmpty()) throw new IllegalArgumentException("Worker 至少需要一个任务 handler");
        Set<Integer> kinds = new HashSet<>();
        for (TaskHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            if (!kinds.add(handler.kind())) throw new IllegalArgumentException("重复的任务 kind：" + handler.kind());
        }
        return handlers;
    }

    private void emitHeartbeat() {
        String current = readConstant(shared.header(), shared.heartbeatIndex(context.shardId()));
        String next = temporary();
        MlogProgramBuilder.Label reset = output.newLabel("runtime_heartbeat_reset");
        MlogProgramBuilder.Label selected = output.newLabel("runtime_heartbeat_selected");
        output.jump(reset, STRICT_EQUAL, current, "null");
        output.jump(reset, GREATER_THAN_EQ, current, MAX_HEARTBEAT);
        output.operation(MlogProgramBuilder.Operation.ADD, next, current, "1");
        output.jump(selected, ALWAYS, "0", "0");
        output.label(reset);
        output.set(next, "1");
        output.label(selected);
        writeConstant(shared.header(), shared.heartbeatIndex(context.shardId()), next);
    }

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
            .orElseThrow(() -> new IllegalArgumentException("共享 Runtime 下标超出物理分配：" + index));
    }

    private PhysicalMemoryLayout.Segment segment(PhysicalMemoryLayout.Slice slice) {
        return memoryLayout.segments().get(slice.segmentIndex());
    }

    private String temporary() {
        return "__mpl_task" + temporaryIndex++;
    }

    record TaskHandler(int kind, TaskBody body) {
        TaskHandler {
            if (kind <= SHUTDOWN_KIND || kind > 2_000_000_000) {
                throw new IllegalArgumentException("任务 kind 必须位于 1..2000000000：" + kind);
            }
            Objects.requireNonNull(body, "body");
        }
    }

    @FunctionalInterface
    interface TaskBody {
        List<String> emit(List<String> requestPayload);
    }
}
