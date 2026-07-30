package com.arc.mpl.codegen;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedMailboxLayout;
import com.arc.mpl.memory.SharedRuntimeLayout;
import com.arc.mpl.project.RuntimePreferences;

import java.util.Objects;

import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.ALWAYS;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.EQUAL;
import static com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition.NOT_EQUAL;

/** Emits the compiler-private, two-phase shared-Memory startup protocol for one shard. */
final class SharedRuntimeStartupEmitter {
    private final MlogProgramBuilder output;
    private final PhysicalMemoryLayout memoryLayout;
    private final MlogRuntimeContext context;
    private int temporaryIndex;

    SharedRuntimeStartupEmitter(MlogProgramBuilder output, PhysicalMemoryLayout memoryLayout,
                                MlogRuntimeContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.memoryLayout = Objects.requireNonNull(memoryLayout, "memoryLayout");
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Main resets and announces the deployment; Workers acknowledge observing ready=0. */
    void emitPreparation() {
        context.sharedRuntime().ifPresent(shared -> {
            if (context.main()) {
                MlogProgramBuilder.Label wait = label("runtime_memory_wait");
                output.label(wait);
                verifyMemoryLinks(wait);
                emitMainPreparation(shared);
            }
            else emitWorkerStartup(shared);
        });
    }

    /** Main publishes initialized shared storage and waits for every positive acknowledgement. */
    void emitReady() {
        context.sharedRuntime().filter(ignored -> context.main()).ifPresent(shared -> {
            writeConstant(shared.header(), SharedRuntimeLayout.READY_INDEX, "1");
            MlogProgramBuilder.Label wait = label("runtime_workers_wait");
            output.label(wait);
            for (String worker : shared.workers()) {
                String heartbeat = readConstant(shared.header(), shared.heartbeatIndex(worker));
                output.jump(wait, NOT_EQUAL, heartbeat, Integer.toString(shared.epoch()));
            }
        });
    }

    private void emitMainPreparation(SharedRuntimeLayout shared) {
        writeConstant(shared.header(), SharedRuntimeLayout.READY_INDEX, "0");
        writeConstant(shared.header(), SharedRuntimeLayout.MAGIC_INDEX,
            Integer.toString(SharedRuntimeLayout.MAGIC));
        writeConstant(shared.header(), SharedRuntimeLayout.ABI_INDEX,
            Integer.toString(SharedRuntimeLayout.ABI_VERSION));
        writeConstant(shared.header(), SharedRuntimeLayout.FINGERPRINT_INDEX,
            Integer.toString(shared.fingerprint()));
        writeConstant(shared.header(), SharedRuntimeLayout.EPOCH_INDEX,
            Integer.toString(shared.epoch()));
        for (String worker : shared.workers()) {
            writeConstant(shared.header(), shared.heartbeatIndex(worker), "0");
        }

        MlogProgramBuilder.Label wait = label("runtime_reset_wait");
        output.label(wait);
        String resetAck = Integer.toString(-shared.epoch());
        for (String worker : shared.workers()) {
            String heartbeat = readConstant(shared.header(), shared.heartbeatIndex(worker));
            output.jump(wait, NOT_EQUAL, heartbeat, resetAck);
        }
        for (SharedMailboxLayout mailbox : shared.mailboxes()) {
            writeConstant(mailbox.allocation(), SharedMailboxLayout.VERSION_INDEX, "0");
            writeConstant(mailbox.allocation(), SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX, "0");
        }
    }

    private void emitWorkerStartup(SharedRuntimeLayout shared) {
        MlogProgramBuilder.Label resetWait = label("runtime_reset_wait");
        MlogProgramBuilder.Label readyWait = label("runtime_ready_wait");
        MlogProgramBuilder.Label resetAck = label("runtime_reset_ack");
        MlogProgramBuilder.Label readyDone = label("runtime_ready_done");
        output.label(resetWait);
        verifyMemoryLinks(resetWait);
        verifyConstant(resetWait, shared.header(), SharedRuntimeLayout.MAGIC_INDEX,
            Integer.toString(SharedRuntimeLayout.MAGIC));
        verifyConstant(resetWait, shared.header(), SharedRuntimeLayout.ABI_INDEX,
            Integer.toString(SharedRuntimeLayout.ABI_VERSION));
        verifyConstant(resetWait, shared.header(), SharedRuntimeLayout.FINGERPRINT_INDEX,
            Integer.toString(shared.fingerprint()));
        verifyConstant(resetWait, shared.header(), SharedRuntimeLayout.EPOCH_INDEX,
            Integer.toString(shared.epoch()));
        verifyConstant(resetWait, shared.header(), SharedRuntimeLayout.READY_INDEX, "0");
        writeConstant(shared.header(), shared.heartbeatIndex(context.shardId()),
            Integer.toString(-shared.epoch()));

        output.label(readyWait);
        String ready = readConstant(shared.header(), SharedRuntimeLayout.READY_INDEX);
        output.jump(resetAck, EQUAL, ready, "0");
        output.jump(readyWait, NOT_EQUAL, ready, "1");
        verifyMemoryLinks(readyWait);
        verifyConstant(readyWait, shared.header(), SharedRuntimeLayout.MAGIC_INDEX,
            Integer.toString(SharedRuntimeLayout.MAGIC));
        verifyConstant(readyWait, shared.header(), SharedRuntimeLayout.ABI_INDEX,
            Integer.toString(SharedRuntimeLayout.ABI_VERSION));
        verifyConstant(readyWait, shared.header(), SharedRuntimeLayout.FINGERPRINT_INDEX,
            Integer.toString(shared.fingerprint()));
        verifyConstant(readyWait, shared.header(), SharedRuntimeLayout.EPOCH_INDEX,
            Integer.toString(shared.epoch()));
        verifyConstant(readyWait, shared.header(), SharedRuntimeLayout.READY_INDEX, "1");
        writeConstant(shared.header(), shared.heartbeatIndex(context.shardId()),
            Integer.toString(shared.epoch()));
        output.jump(readyDone, ALWAYS, "0", "0");
        output.label(resetAck);
        writeConstant(shared.header(), shared.heartbeatIndex(context.shardId()),
            Integer.toString(-shared.epoch()));
        output.jump(readyWait, ALWAYS, "0", "0");
        output.label(readyDone);
    }

    private void verifyMemoryLinks(MlogProgramBuilder.Label wait) {
        for (PhysicalMemoryLayout.Segment segment : memoryLayout.segments()) {
            String actualType = temporary();
            output.sensor(actualType, segment.alias(), "@type");
            String expected = segment.kind() == RuntimePreferences.MemoryKind.CELL
                ? "@memory-cell" : "@memory-bank";
            output.jump(wait, NOT_EQUAL, actualType, expected);
        }
    }

    private void verifyConstant(MlogProgramBuilder.Label wait, PhysicalMemoryLayout.Allocation allocation,
                                int index, String expected) {
        output.jump(wait, NOT_EQUAL, readConstant(allocation, index), expected);
    }

    /** Invalid v146 Memory reads preserve their destination, so reset it before every attempt. */
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

    private MlogProgramBuilder.Label label(String role) {
        return output.newLabel(role);
    }

    private String temporary() {
        return "__mpl_runtime" + temporaryIndex++;
    }
}
