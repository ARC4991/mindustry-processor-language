package com.arc.mpl.project;

import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeHelperPlannerTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final HirFunction add = new HirFunction("add", List.of(
        new HirFunctionParameter("left", ValueType.INT), new HirFunctionParameter("right", ValueType.INT)),
        ValueType.INT, List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("left", ValueType.INT), "+",
            new HirVariable("right", ValueType.INT), ValueType.INT)))));

    @Test
    void mapsPureFunctionsToStableKindsAndPairedMailboxes() {
        HirProgram program = new HirProgram(List.of(add), List.of());
        RuntimePreferences performance = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 2), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        RuntimeHelperPlan plan = new RuntimeHelperPlanner().plan(program,
            new HirEffectAnalyzer().analyze(program), "stop\n", profile, performance);

        assertTrue(plan.enabled());
        assertEquals(1, plan.task("add").orElseThrow().kind());
        assertEquals("Worker-0", plan.task("add").orElseThrow().worker());
        assertEquals(2, plan.workers().get(0).requestPayloadSlots());
        assertEquals(List.of("MainToWorker0", "Worker0ToMain"), plan.mailboxRequirements().stream()
            .map(com.arc.mpl.memory.SharedRuntimeLayoutPlanner.MailboxRequirement::id).toList());
        assertEquals(List.of(2, 1), plan.mailboxRequirements().stream()
            .map(com.arc.mpl.memory.SharedRuntimeLayoutPlanner.MailboxRequirement::payloadSlots).toList());
    }

    @Test
    void keepsSmallResourceBuildsSingleShardAndHonorsProcessorLimits() {
        HirProgram program = new HirProgram(List.of(add), List.of());
        HirEffectAnalyzer.Analysis effects = new HirEffectAnalyzer().analyze(program);
        RuntimePreferences minimal = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            Map.of(TargetProfile.ProcessorKind.MICRO, 2), Map.of(RuntimePreferences.MemoryKind.BANK, 1));
        RuntimePreferences oneProcessor = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 1), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        assertFalse(new RuntimeHelperPlanner().plan(program, effects, "stop\n", profile, minimal).enabled());
        assertFalse(new RuntimeHelperPlanner().plan(program, effects, "stop\n", profile, oneProcessor).enabled());
    }
}
