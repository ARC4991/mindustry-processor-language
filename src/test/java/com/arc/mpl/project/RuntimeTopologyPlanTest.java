package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.arc.mpl.hir.ValueType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeTopologyPlanTest {
    @Test
    void aggregatesShardResourcesWithoutDuplicatingSharedMemory() {
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "shared");
        PhysicalMemoryLayout memory = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("bank1", RuntimePreferences.MemoryKind.BANK, 512, 9)
        ), java.util.Map.of(key, new PhysicalMemoryLayout.Allocation(key, 9,
            List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 9)))), 9);
        RuntimeTopologyPlan plan = new RuntimeTopologyPlan(List.of(
            new ShardPlan("Main", List.of("main", "io"), TargetProfile.ProcessorKind.MICRO, 10, 2, 4, 3),
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.LOGIC, 20, 4, 5, 7)
        ), memory);

        assertEquals("Main", plan.main().id());
        assertEquals(30, plan.instructions());
        assertEquals(6, plan.labels());
        assertEquals(10, plan.virtualSlots());
        assertEquals(9, plan.physicalSlots());
    }

    @Test
    void requiresUniqueShardIdsAndExactlyOneMain() {
        ShardPlan main = new ShardPlan("Main", List.of("main"), TargetProfile.ProcessorKind.MICRO,
            1, 0, 1, 0);

        assertThrows(IllegalArgumentException.class, () -> new RuntimeTopologyPlan(List.of(
            main, new ShardPlan("Main", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 1, 0, 1, 0)
        ), PhysicalMemoryLayout.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTopologyPlan(List.of(
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 1, 0, 1, 0)
        ), PhysicalMemoryLayout.empty()));
    }

    @Test
    void validatesSharedRuntimeIdentityAndCountsItsSlotsOnce() {
        List<RuntimePlanner.ShardSource> sources = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "main"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "worker"));
        SharedRuntimeLayoutPlanner.Result prepared = new RuntimePlanner().prepareSharedRuntime(sources,
            KnownProfiles.find("v146").orElseThrow(), RuntimePreferences.defaults(), PhysicalMemoryLayout.empty());
        List<ShardPlan> shards = List.of(
            new ShardPlan("Main", List.of("main"), TargetProfile.ProcessorKind.MICRO, 10, 1, 4, 2),
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 10, 1, 4, 2));

        RuntimeTopologyPlan topology = new RuntimeTopologyPlan(shards, prepared.physicalMemoryLayout(),
            java.util.Optional.of(prepared.sharedRuntime()));

        assertEquals(7, topology.runtimeSlots());
        assertEquals(7, topology.physicalSlots());
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTopologyPlan(List.of(
            shards.get(0), new ShardPlan("Worker-1", List.of("worker"), TargetProfile.ProcessorKind.MICRO,
                10, 1, 4, 2)), prepared.physicalMemoryLayout(), java.util.Optional.of(prepared.sharedRuntime())));
    }

    @Test
    void requiresHelperWorkersToMatchAnEnabledSharedRuntime() {
        RuntimeHelperPlan helpers = new RuntimeHelperPlan(List.of(
            new RuntimeHelperPlan.Worker("Worker-0", "MainToWorker0", "Worker0ToMain", 2, List.of("add"))
        ), Map.of("add", new RuntimeHelperPlan.Task("add", "Worker-0", 1,
            List.of(ValueType.INT, ValueType.INT), ValueType.INT)));
        List<RuntimePlanner.ShardSource> sources = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "main"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "worker"));
        SharedRuntimeLayoutPlanner.Result prepared = new RuntimePlanner().prepareSharedRuntime(sources,
            KnownProfiles.find("v146").orElseThrow(), RuntimePreferences.defaults(), PhysicalMemoryLayout.empty());
        List<ShardPlan> shards = List.of(
            new ShardPlan("Main", List.of("main"), TargetProfile.ProcessorKind.MICRO, 1, 0, 2, 0),
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 1, 0, 2, 0));

        RuntimeTopologyPlan topology = new RuntimeTopologyPlan(shards, prepared.physicalMemoryLayout(),
            Optional.of(prepared.sharedRuntime()), Optional.of(helpers));

        assertEquals(helpers, topology.helperPlan().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTopologyPlan(shards,
            prepared.physicalMemoryLayout(), Optional.empty(), Optional.of(helpers)));

        RuntimeHelperPlan wrongWorker = new RuntimeHelperPlan(List.of(
            new RuntimeHelperPlan.Worker("Worker-1", "MainToWorker1", "Worker1ToMain", 2, List.of("add"))
        ), Map.of("add", new RuntimeHelperPlan.Task("add", "Worker-1", 1,
            List.of(ValueType.INT, ValueType.INT), ValueType.INT)));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTopologyPlan(shards,
            prepared.physicalMemoryLayout(), Optional.of(prepared.sharedRuntime()), Optional.of(wrongWorker)));
    }
}
