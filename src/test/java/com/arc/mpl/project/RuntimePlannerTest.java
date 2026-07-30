package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimePlannerTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();

    @Test
    void honorsTheUserProcessorConstraintAndOptimizationGoal() {
        RuntimePlan economical = new RuntimePlanner().plan("set value 1\n", profile, RuntimePreferences.defaults());
        RuntimePlan fast = new RuntimePlanner().plan("set value 1\n", profile,
            new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
                java.util.Map.of(TargetProfile.ProcessorKind.LOGIC, 1, TargetProfile.ProcessorKind.HYPER, 1),
                RuntimePreferences.defaults().memory()));

        assertEquals(TargetProfile.ProcessorKind.MICRO, economical.processor());
        assertEquals(TargetProfile.ProcessorKind.HYPER, fast.processor());
    }

    @Test
    void doesNotUpgradeProcessorMerelyBecauseTheProgramSpansMultipleTicks() {
        String program = String.join("\n", java.util.Collections.nCopies(100, "set value 1")) + "\n";

        RuntimePlan plan = new RuntimePlanner().plan(program, profile, RuntimePreferences.defaults());

        assertEquals(100, plan.instructions());
        assertEquals(TargetProfile.ProcessorKind.MICRO, plan.processor());
    }

    @Test
    void rejectsProgramsBeyondTheTargetInstructionLimit() {
        String program = String.join("\n", java.util.Collections.nCopies(profile.maxInstructions() + 1, "set value 1")) + "\n";

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new RuntimePlanner().plan(program, profile, RuntimePreferences.defaults()));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("超过 target v146"));
    }

    @Test
    void plansMemoryFromTheSameUserConstraints() {
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), java.util.Map.of(RuntimePreferences.MemoryKind.CELL, 2));
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.CELL, 64, 64),
            new PhysicalMemoryLayout.Segment("__mpl_mem1", RuntimePreferences.MemoryKind.CELL, 64, 36)
        ), java.util.Map.of(key, new PhysicalMemoryLayout.Allocation(key, 100, List.of(
            new PhysicalMemoryLayout.Slice(0, 0, 0, 64),
            new PhysicalMemoryLayout.Slice(1, 0, 64, 36)
        ))), 100);
        RuntimePlan plan = new RuntimePlanner().plan("set value 1\n", profile, preferences, layout);

        assertEquals(2, plan.memoryCells());
        assertEquals(0, plan.memoryBanks());
    }

    @Test
    void rejectsALayoutThatDoesNotMatchTheRuntimeMemoryLimits() {
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(
            List.of(new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.BANK, 512, 1)),
            java.util.Map.of(key, new PhysicalMemoryLayout.Allocation(key, 1,
                List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 1)))), 1);
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), java.util.Map.of(RuntimePreferences.MemoryKind.CELL, 1));

        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePlanner().plan("stop\n", profile, preferences, layout));
    }

    @Test
    void plansEveryShardWithinTheGlobalProcessorCounts() {
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            java.util.Map.of(TargetProfile.ProcessorKind.MICRO, 1, TargetProfile.ProcessorKind.LOGIC, 1),
            RuntimePreferences.defaults().memory());

        RuntimeTopologyPlan plan = new RuntimePlanner().planTopology(List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "set mpl_main 1\nstop\n"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "set mpl_worker 1\nstop\n")
        ), profile, preferences, PhysicalMemoryLayout.empty());

        assertEquals(List.of(TargetProfile.ProcessorKind.MICRO, TargetProfile.ProcessorKind.LOGIC),
            plan.shards().stream().map(ShardPlan::processor).toList());
        assertEquals(4, plan.instructions());
        assertEquals(2, plan.virtualSlots());
    }

    @Test
    void rejectsInsufficientProcessorsAndOversizedIndividualShards() {
        RuntimePreferences oneProcessor = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            java.util.Map.of(TargetProfile.ProcessorKind.MICRO, 1), RuntimePreferences.defaults().memory());
        List<RuntimePlanner.ShardSource> twoShards = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), "stop\n"),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), "stop\n")
        );
        IllegalArgumentException count = assertThrows(IllegalArgumentException.class,
            () -> new RuntimePlanner().planTopology(twoShards, profile, oneProcessor, PhysicalMemoryLayout.empty()));
        org.junit.jupiter.api.Assertions.assertTrue(count.getMessage().contains("处理器数量不足"));

        String oversized = String.join("\n",
            java.util.Collections.nCopies(profile.maxInstructions() + 1, "set value 1")) + "\n";
        IllegalArgumentException limit = assertThrows(IllegalArgumentException.class,
            () -> new RuntimePlanner().planTopology(List.of(
                new RuntimePlanner.ShardSource("Main", List.of("main"), "stop\n"),
                new RuntimePlanner.ShardSource("Worker-0", List.of("worker"), oversized)
            ), profile, RuntimePreferences.defaults(), PhysicalMemoryLayout.empty()));
        org.junit.jupiter.api.Assertions.assertTrue(limit.getMessage().contains("shard Worker-0"));
    }
}
