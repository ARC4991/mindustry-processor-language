package com.arc.mpl.codegen;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedMailboxLayout;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePlanner;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedMailboxProtocolEmitterTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final SharedRuntimeLayoutPlanner.Result prepared = prepare();

    @Test
    void producerPublishesPayloadBetweenOddAndEvenVersions() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedMailboxProtocolEmitter emitter = new SharedMailboxProtocolEmitter(output,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));

        String committed = emitter.emitSend("Requests", "7", List.of("11", "13"));
        output.stop();
        String mlog = output.render();

        assertEquals("__mpl_mailbox3", committed);
        assertEquals("""
            _0:
            set __mpl_mailbox0 null
            read __mpl_mailbox0 bank1 7
            jump _0 strictEqual __mpl_mailbox0 null
            set __mpl_mailbox1 null
            read __mpl_mailbox1 bank1 8
            jump _0 strictEqual __mpl_mailbox1 null
            jump _0 notEqual __mpl_mailbox0 __mpl_mailbox1
            jump _0 lessThan __mpl_mailbox0 0
            op mod __mpl_mailbox2 __mpl_mailbox0 2
            jump _0 notEqual __mpl_mailbox2 0
            jump _1 lessThan __mpl_mailbox0 2000000000
            set __mpl_mailbox3 2
            jump _2 always 0 0
            _1:
            op add __mpl_mailbox3 __mpl_mailbox0 2
            _2:
            op sub __mpl_mailbox4 __mpl_mailbox3 1
            write __mpl_mailbox4 bank1 7
            write 7 bank1 9
            write 11 bank1 10
            write 13 bank1 11
            write __mpl_mailbox3 bank1 7
            stop
            """, mlog);
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void consumerDoubleReadsVersionAndConfirmsItsAcknowledgement() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedMailboxProtocolEmitter emitter = new SharedMailboxProtocolEmitter(output,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Worker-0", prepared.sharedRuntime()));

        SharedMailboxProtocolEmitter.ReceivedMessage message = emitter.emitReceive("Requests");
        output.stop();
        String mlog = output.render();

        assertEquals("__mpl_mailbox6", message.version());
        assertEquals("__mpl_mailbox3", message.kind());
        assertEquals(List.of("__mpl_mailbox4", "__mpl_mailbox5"), message.payload());
        assertTrue(mlog.contains("op mod __mpl_mailbox1 __mpl_mailbox0 2"));
        assertTrue(mlog.contains("jump _0 notEqual __mpl_mailbox0 __mpl_mailbox6"));
        assertTrue(mlog.contains("write __mpl_mailbox6 bank1 8"));
        assertTrue(mlog.contains("read __mpl_mailbox7 bank1 8"));
        assertTrue(mlog.contains("jump _0 notEqual __mpl_mailbox7 __mpl_mailbox6"));
        assertEquals(7, count(mlog, " null\nread "));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void emitsIdleHooksOnRetryAndCanAwaitTheExactAcknowledgement() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedMailboxProtocolEmitter emitter = new SharedMailboxProtocolEmitter(output,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));

        String committed = emitter.emitSend("Requests", "7", List.of("11", "13"),
            () -> output.set("sendIdle", "1"));
        emitter.emitAwaitAcknowledged("Requests", committed, () -> output.set("ackIdle", "1"));
        output.stop();
        String mlog = output.render();

        assertEquals(1, count(mlog, "set sendIdle 1\n"));
        assertEquals(1, count(mlog, "set ackIdle 1\n"));
        assertTrue(mlog.contains("read __mpl_mailbox5 bank1 8\n"));
        assertTrue(mlog.contains("jump _3 notEqual __mpl_mailbox5 __mpl_mailbox3\n"));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void enforcesStaticOwnershipWidthAndNumericSentinelBoundary() {
        MlogProgramBuilder mainOutput = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedMailboxProtocolEmitter main = new SharedMailboxProtocolEmitter(mainOutput,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));
        MlogProgramBuilder workerOutput = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedMailboxProtocolEmitter worker = new SharedMailboxProtocolEmitter(workerOutput,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Worker-0", prepared.sharedRuntime()));

        assertThrows(IllegalArgumentException.class, () -> worker.emitSend("Requests", "1", List.of("2", "3")));
        assertThrows(IllegalArgumentException.class, () -> main.emitReceive("Requests"));
        assertThrows(IllegalArgumentException.class, () -> main.emitSend("Requests", "1", List.of("2")));
        assertThrows(IllegalArgumentException.class, () -> main.emitSend("Requests", "null", List.of("2", "3")));
    }

    @Test
    void mainClearsMailboxStateBeforePublishingReady() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedRuntimeStartupEmitter startup = new SharedRuntimeStartupEmitter(output,
            prepared.physicalMemoryLayout(), MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));

        startup.emitPreparation();
        startup.emitReady();
        output.stop();
        String mlog = output.render();
        SharedMailboxLayout mailbox = prepared.sharedRuntime().mailbox("Requests");
        String clearVersion = constantWrite(mailbox, SharedMailboxLayout.VERSION_INDEX, "0");
        String clearAcknowledged = constantWrite(mailbox, SharedMailboxLayout.ACKNOWLEDGED_VERSION_INDEX, "0");
        String publishReady = constantWrite(prepared.sharedRuntime().header(),
            com.arc.mpl.memory.SharedRuntimeLayout.READY_INDEX, "1");

        assertTrue(mlog.indexOf(clearVersion) < mlog.indexOf(publishReady));
        assertTrue(mlog.indexOf(clearAcknowledged) < mlog.indexOf(publishReady));
    }

    private SharedRuntimeLayoutPlanner.Result prepare() {
        List<RuntimePlanner.ShardSource> shards = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "main"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "worker"));
        return new RuntimePlanner().prepareSharedRuntime(shards, profile, RuntimePreferences.defaults(),
            PhysicalMemoryLayout.empty(), List.of(new SharedRuntimeLayoutPlanner.MailboxRequirement(
                "Requests", "Main", "Worker-0", 2)));
    }

    private String constantWrite(SharedMailboxLayout mailbox, int index, String value) {
        return constantWrite(mailbox.allocation(), index, value);
    }

    private String constantWrite(PhysicalMemoryLayout.Allocation allocation, int index, String value) {
        PhysicalMemoryLayout.Slice slice = allocation.slices().stream()
            .filter(candidate -> candidate.contains(index)).findFirst().orElseThrow();
        String alias = prepared.physicalMemoryLayout().segments().get(slice.segmentIndex()).alias();
        return "write " + value + " " + alias + " " + (slice.offset() + index - slice.logicalStart());
    }

    private int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
