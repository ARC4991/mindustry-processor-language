package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.PhysicalMemoryPlanner;
import com.arc.mpl.memory.SharedRuntimeLayout;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePlanner;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlogSharedRuntimeTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();

    @Test
    void mainInvalidatesReadyBeforeStorageInitializationAndWaitsForWorkerAck() {
        HirProgram program = new HirProgram(List.of(new HirVariableDeclaration("text", ValueType.STRING, false,
            new HirText("A"), false, 1)));
        RuntimePreferences preferences = RuntimePreferences.defaults();
        PhysicalMemoryLayout base = new PhysicalMemoryPlanner().plan(program, profile, preferences);
        SharedRuntimeLayoutPlanner.Result prepared = prepare(base, preferences);
        SharedRuntimeLayout shared = prepared.sharedRuntime();

        String mlog = generator("Main", prepared).generate(program);
        String readyZero = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            SharedRuntimeLayout.READY_INDEX, "0");
        String readyOne = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            SharedRuntimeLayout.READY_INDEX, "1");
        String magic = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            SharedRuntimeLayout.MAGIC_INDEX, Integer.toString(SharedRuntimeLayout.MAGIC));
        String heartbeatZero = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            shared.heartbeatIndex("Worker-0"), "0");

        assertTrue(mlog.startsWith(readyZero + "\n"));
        assertTrue(mlog.indexOf(magic) > readyZero.length());
        assertTrue(mlog.indexOf(heartbeatZero) < mlog.indexOf(readyOne));
        assertTrue(mlog.indexOf(Integer.toString(-shared.epoch())) < mlog.indexOf(readyOne));
        assertTrue(mlog.contains("set __mpl_tmp"));
        assertTrue(mlog.contains("read __mpl_tmp"));
        assertTrue(mlog.contains("notEqual __mpl_tmp"));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void workerChecksEveryHeaderFieldWithASentinelBeforeAcknowledging() {
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), Map.of(RuntimePreferences.MemoryKind.CELL, 2));
        PhysicalMemoryLayout.StorageKey existingKey = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout.Allocation existing = new PhysicalMemoryLayout.Allocation(existingKey, 63,
            List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 63)));
        PhysicalMemoryLayout base = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("cell__mpl_mem0", RuntimePreferences.MemoryKind.CELL, 64, 63)
        ), Map.of(existingKey, existing), 63);
        SharedRuntimeLayoutPlanner.Result prepared = prepare(base, preferences);
        SharedRuntimeLayout shared = prepared.sharedRuntime();

        String mlog = generator("Worker-0", prepared).generate(new HirProgram(List.of()));

        assertTrue(mlog.startsWith("_0:\nset __mpl_tmp0 null\nread __mpl_tmp0 cell__mpl_mem0 63\n"));
        assertEquals(11, count(mlog, " null\nread "));
        assertTrue(mlog.contains("read __mpl_tmp1 cell__mpl_mem1 0"));
        assertTrue(mlog.contains("read __mpl_tmp4 cell__mpl_mem1 3"));
        String resetAck = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            shared.heartbeatIndex("Worker-0"), Integer.toString(-shared.epoch()));
        String readyAck = constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            shared.heartbeatIndex("Worker-0"), Integer.toString(shared.epoch()));
        assertTrue(mlog.contains(resetAck));
        assertTrue(mlog.indexOf(resetAck) < mlog.indexOf(readyAck));
        assertTrue(mlog.contains(constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            shared.heartbeatIndex("Worker-0"), Integer.toString(shared.epoch()))));
        assertFalse(mlog.contains(constantWrite(prepared.physicalMemoryLayout(), shared.header(),
            shared.heartbeatIndex("Worker-0"), "0")));
        assertTrue(mlog.endsWith("stop\n"));
        assertTrue(new MlogOutputValidator().validate(mlog, profile).isEmpty());
    }

    @Test
    void workerCannotOwnExternalHardware() {
        SharedRuntimeLayoutPlanner.Result prepared = prepare(PhysicalMemoryLayout.empty(),
            RuntimePreferences.defaults());

        assertThrows(IllegalArgumentException.class, () -> new MlogCodeGenerator(MlogLabelStyle.RELEASE,
            prepared.physicalMemoryLayout(), List.of(new MlogCodeGenerator.HardwareRequirement("message1", "message")),
            profile.capabilities(), MlogRuntimeContext.shared("Worker-0", prepared.sharedRuntime())));
    }

    private SharedRuntimeLayoutPlanner.Result prepare(PhysicalMemoryLayout base, RuntimePreferences preferences) {
        List<RuntimePlanner.ShardSource> sources = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "main-seed"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "worker-seed"));
        return new RuntimePlanner().prepareSharedRuntime(sources, profile, preferences, base);
    }

    private MlogCodeGenerator generator(String shard, SharedRuntimeLayoutPlanner.Result prepared) {
        return new MlogCodeGenerator(MlogLabelStyle.RELEASE, prepared.physicalMemoryLayout(), List.of(),
            profile.capabilities(), MlogRuntimeContext.shared(shard, prepared.sharedRuntime()));
    }

    private String constantWrite(PhysicalMemoryLayout layout, PhysicalMemoryLayout.Allocation allocation,
                                 int index, String value) {
        PhysicalMemoryLayout.Slice slice = allocation.slices().stream()
            .filter(candidate -> candidate.contains(index)).findFirst().orElseThrow();
        String alias = layout.segments().get(slice.segmentIndex()).alias();
        return "write " + value + " " + alias + " " + (slice.offset() + index - slice.logicalStart());
    }

    private int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
