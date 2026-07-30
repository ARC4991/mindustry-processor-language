package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimeHelperPlan;
import com.arc.mpl.project.RuntimeHelperPlanner;
import com.arc.mpl.project.RuntimePlanner;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlogHelperShardTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();

    @Test
    void mainCallsPureFunctionThroughMailboxesAndWorkerRunsOriginalBody() {
        HirFunction add = new HirFunction("add", List.of(
            new HirFunctionParameter("left", ValueType.INT), new HirFunctionParameter("right", ValueType.INT)),
            ValueType.INT, List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("left", ValueType.INT), "+",
                new HirVariable("right", ValueType.INT), ValueType.INT)))));
        HirProgram program = new HirProgram(List.of(add), List.of(new HirVariableDeclaration("result", ValueType.INT,
            false, new HirFunctionCall("add", List.of(new HirConstant("11", ValueType.INT),
                new HirConstant("13", ValueType.INT)), ValueType.INT))));
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MAX_PERFORMANCE,
            Map.of(TargetProfile.ProcessorKind.MICRO, 2), Map.of(RuntimePreferences.MemoryKind.BANK, 1));
        String baseline = new MlogCodeGenerator().generate(program);
        RuntimeHelperPlan helpers = new RuntimeHelperPlanner().plan(program,
            new HirEffectAnalyzer().analyze(program), baseline, profile, preferences);
        List<RuntimePlanner.ShardSource> seeds = List.of(
            new RuntimePlanner.ShardSource("Main", List.of("main"), baseline),
            new RuntimePlanner.ShardSource("Worker-0", List.of("worker", "numeric-helper"), "worker"));
        SharedRuntimeLayoutPlanner.Result prepared = new RuntimePlanner().prepareSharedRuntime(
            seeds, profile, preferences, PhysicalMemoryLayout.empty(), helpers.mailboxRequirements());

        String main = generator("Main", prepared, helpers).generate(program);
        String worker = generator("Worker-0", prepared, helpers).generate(program);

        assertTrue(main.contains("write 1 bank1 9\n"), main);
        assertTrue(main.contains("write __mpl_tmp0 bank1 10\n"), main);
        assertTrue(main.contains("write __mpl_tmp1 bank1 11\n"), main);
        assertTrue(main.contains("write 0 bank1 9\n"), main);
        assertFalse(main.contains("mpl_function_add"), main);
        assertTrue(worker.contains("op add __mpl_tmp"), worker);
        assertTrue(worker.contains("jump mpl_runtime_task_shutdown"), worker);
        assertTrue(worker.contains("mpl_function_add"), worker);
        assertTrue(new MlogOutputValidator().validate(main, profile).isEmpty());
        assertTrue(new MlogOutputValidator().validate(worker, profile).isEmpty());
    }

    private MlogCodeGenerator generator(String shard, SharedRuntimeLayoutPlanner.Result prepared,
                                        RuntimeHelperPlan helpers) {
        return new MlogCodeGenerator(MlogLabelStyle.DEBUG, prepared.physicalMemoryLayout(), List.of(),
            profile.capabilities(), MlogRuntimeContext.shared(shard, prepared.sharedRuntime(), helpers));
    }
}
