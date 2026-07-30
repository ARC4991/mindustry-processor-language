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
        HirProgram program = calledProgram();
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
        HirProgram program = calledProgram();
        HirEffectAnalyzer.Analysis effects = new HirEffectAnalyzer().analyze(program);
        RuntimePreferences minimal = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            Map.of(TargetProfile.ProcessorKind.MICRO, 2), Map.of(RuntimePreferences.MemoryKind.BANK, 1));
        RuntimePreferences oneProcessor = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 1), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        assertFalse(new RuntimeHelperPlanner().plan(program, effects, "stop\n", profile, minimal).enabled());
        assertFalse(new RuntimeHelperPlanner().plan(program, effects, "stop\n", profile, oneProcessor).enabled());
    }

    @Test
    void doesNotAllocateAWorkerForAnUnreachablePureFunction() {
        HirProgram unused = new HirProgram(List.of(add), List.of());
        RuntimePreferences performance = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 2), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        assertFalse(new RuntimeHelperPlanner().plan(unused, new HirEffectAnalyzer().analyze(unused),
            "stop\n", profile, performance).enabled());
    }

    @Test
    void distributesIndependentHelpersAcrossAllAvailableWorkers() {
        HirFunction subtract = binary("subtract", "-");
        HirFunction multiply = binary("multiply", "*");
        HirProgram program = new HirProgram(List.of(add, subtract, multiply), List.of(
            call("add"), call("subtract"), call("multiply")));
        RuntimePreferences performance = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 4), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        RuntimeHelperPlan plan = new RuntimeHelperPlanner().plan(program,
            new HirEffectAnalyzer().analyze(program), "stop\n", profile, performance);

        assertEquals(List.of("Worker-0", "Worker-1", "Worker-2"), plan.workers().stream()
            .map(RuntimeHelperPlan.Worker::id).toList());
        assertEquals(List.of(List.of("add"), List.of("subtract"), List.of("multiply")), plan.workers().stream()
            .map(RuntimeHelperPlan.Worker::functions).toList());
        assertEquals(List.of("Worker-0", "Worker-1", "Worker-2"), List.of(
            plan.task("add").orElseThrow().worker(), plan.task("subtract").orElseThrow().worker(),
            plan.task("multiply").orElseThrow().worker()));
        assertEquals(List.of(1, 2, 3), List.of(plan.task("add").orElseThrow().kind(),
            plan.task("subtract").orElseThrow().kind(), plan.task("multiply").orElseThrow().kind()));
        assertEquals(6, plan.mailboxRequirements().size());
    }

    @Test
    void keepsAHelperCallChainOnOneWorker() {
        HirFunction inner = new HirFunction("inner", List.of(new HirFunctionParameter("value", ValueType.INT)),
            ValueType.INT, List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("value", ValueType.INT),
                "+", new com.arc.mpl.hir.HirConstant("1", ValueType.INT), ValueType.INT)))));
        HirFunction outer = new HirFunction("outer", List.of(new HirFunctionParameter("value", ValueType.INT)),
            ValueType.INT, List.of(new HirReturn(Optional.of(new com.arc.mpl.hir.HirFunctionCall("inner",
                List.of(new HirVariable("value", ValueType.INT)), ValueType.INT)))));
        HirFunction independent = binary("independent", "-");
        HirProgram program = new HirProgram(List.of(inner, outer, independent), List.of(
            new com.arc.mpl.hir.HirExpressionStatement(new com.arc.mpl.hir.HirFunctionCall("outer",
                List.of(new com.arc.mpl.hir.HirConstant("3", ValueType.INT)), ValueType.INT)),
            call("independent")));
        RuntimePreferences performance = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 3), Map.of(RuntimePreferences.MemoryKind.BANK, 1));

        RuntimeHelperPlan plan = new RuntimeHelperPlanner().plan(program,
            new HirEffectAnalyzer().analyze(program), "stop\n", profile, performance);

        assertEquals(2, plan.workers().size());
        assertEquals(plan.task("inner").orElseThrow().worker(), plan.task("outer").orElseThrow().worker());
        assertFalse(plan.task("inner").orElseThrow().worker()
            .equals(plan.task("independent").orElseThrow().worker()));
    }

    private HirProgram calledProgram() {
        return new HirProgram(List.of(add), List.of(new com.arc.mpl.hir.HirExpressionStatement(
            new com.arc.mpl.hir.HirFunctionCall("add", List.of(
                new com.arc.mpl.hir.HirConstant("1", ValueType.INT),
                new com.arc.mpl.hir.HirConstant("2", ValueType.INT)), ValueType.INT))));
    }

    private HirFunction binary(String name, String operator) {
        return new HirFunction(name, List.of(
            new HirFunctionParameter("left", ValueType.INT), new HirFunctionParameter("right", ValueType.INT)),
            ValueType.INT, List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("left", ValueType.INT),
                operator, new HirVariable("right", ValueType.INT), ValueType.INT)))));
    }

    private com.arc.mpl.hir.HirExpressionStatement call(String function) {
        return new com.arc.mpl.hir.HirExpressionStatement(new com.arc.mpl.hir.HirFunctionCall(function, List.of(
            new com.arc.mpl.hir.HirConstant("4", ValueType.INT),
            new com.arc.mpl.hir.HirConstant("2", ValueType.INT)), ValueType.INT));
    }
}
