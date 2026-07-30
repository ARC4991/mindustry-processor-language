package com.arc.mpl.codegen;

import com.arc.mpl.memory.PhysicalMemoryLayout;
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

class SharedRuntimeTaskEmitterTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final SharedRuntimeLayoutPlanner.Result prepared = prepare();

    @Test
    void workerPollsWithHeartbeatDispatchesAndStopsOnlyForShutdown() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        MlogRuntimeContext context = MlogRuntimeContext.shared("Worker-0", prepared.sharedRuntime());
        SharedRuntimeTaskEmitter emitter = new SharedRuntimeTaskEmitter(
            output, prepared.physicalMemoryLayout(), context);

        emitter.emitWorkerLoop("Requests", "Responses", List.of(
            new SharedRuntimeTaskEmitter.TaskHandler(7, payload -> {
                output.operation(MlogProgramBuilder.Operation.ADD, "__sum", payload.get(0), payload.get(1));
                return List.of("__sum");
            })));
        String mlog = output.render();

        assertTrue(mlog.contains("jump _1 equal __mpl_mailbox3 0\n"));
        assertTrue(mlog.contains("jump _2 equal __mpl_mailbox3 7\n"));
        assertTrue(mlog.contains("op add __sum __mpl_mailbox4 __mpl_mailbox5\n"));
        assertTrue(mlog.contains("write __mpl_task1 bank__mpl_mem0 6\n"));
        assertTrue(mlog.contains("write __mpl_task3 bank__mpl_mem0 6\n"));
        assertEquals(1, count(mlog, "stop\n"));
        assertTrue(mlog.endsWith("stop\n"));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void mainPublishesZeroKindAndWaitsForWorkerConsumption() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedRuntimeTaskEmitter emitter = new SharedRuntimeTaskEmitter(output, prepared.physicalMemoryLayout(),
            MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));

        emitter.emitShutdownAndAwait("Requests");
        output.stop();
        String mlog = output.render();

        assertTrue(mlog.contains("write 0 bank__mpl_mem0 9\n"));
        assertTrue(mlog.contains("write 0 bank__mpl_mem0 10\n"));
        assertTrue(mlog.contains("write 0 bank__mpl_mem0 11\n"));
        assertTrue(mlog.contains("read __mpl_mailbox5 bank__mpl_mem0 8\n"));
        assertTrue(mlog.contains("jump _3 notEqual __mpl_mailbox5 __mpl_mailbox3\n"));
        assertTrue(mlog.endsWith("stop\n"));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void rejectsInvalidRolesAndTaskKindsAtGenerationTime() {
        MlogProgramBuilder output = new MlogProgramBuilder(MlogLabelStyle.RELEASE);
        SharedRuntimeTaskEmitter main = new SharedRuntimeTaskEmitter(output, prepared.physicalMemoryLayout(),
            MlogRuntimeContext.shared("Main", prepared.sharedRuntime()));
        SharedRuntimeTaskEmitter worker = new SharedRuntimeTaskEmitter(output, prepared.physicalMemoryLayout(),
            MlogRuntimeContext.shared("Worker-0", prepared.sharedRuntime()));

        assertThrows(IllegalArgumentException.class, () -> main.emitWorkerLoop("Requests", "Responses", List.of(
            new SharedRuntimeTaskEmitter.TaskHandler(1, ignored -> List.of("0")))));
        assertThrows(IllegalArgumentException.class, () -> worker.emitShutdownAndAwait("Requests"));
        assertThrows(IllegalArgumentException.class, () -> new SharedRuntimeTaskEmitter.TaskHandler(
            SharedRuntimeTaskEmitter.SHUTDOWN_KIND, ignored -> List.of("0")));
        assertThrows(IllegalArgumentException.class, () -> worker.emitWorkerLoop("Requests", "Responses", List.of(
            new SharedRuntimeTaskEmitter.TaskHandler(1, ignored -> List.of("0")),
            new SharedRuntimeTaskEmitter.TaskHandler(1, ignored -> List.of("0")))));
    }

    private SharedRuntimeLayoutPlanner.Result prepare() {
        List<RuntimePlanner.ShardSource> shards = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "main"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "worker"));
        return new RuntimePlanner().prepareSharedRuntime(shards, profile, RuntimePreferences.defaults(),
            PhysicalMemoryLayout.empty(), List.of(
                new SharedRuntimeLayoutPlanner.MailboxRequirement("Requests", "Main", "Worker-0", 2),
                new SharedRuntimeLayoutPlanner.MailboxRequirement("Responses", "Worker-0", "Main", 1)));
    }

    private int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
