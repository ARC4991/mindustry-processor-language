package com.arc.mpl.project;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        RuntimePlan plan = new RuntimePlanner().plan("set value 1\n", profile, preferences, 100);

        assertEquals(2, plan.memoryCells());
        assertEquals(0, plan.memoryBanks());
    }
}
