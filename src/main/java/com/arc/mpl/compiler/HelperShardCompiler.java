package com.arc.mpl.compiler;

import com.arc.mpl.codegen.MilCodeGenerator;
import com.arc.mpl.codegen.MlogCodeGenerator;
import com.arc.mpl.codegen.MlogCodeGenerator.HardwareRequirement;
import com.arc.mpl.codegen.MlogLabelStyle;
import com.arc.mpl.codegen.MlogRuntimeContext;
import com.arc.mpl.codegen.MlogProgramMetrics;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimeHelperPlan;
import com.arc.mpl.project.RuntimeHelperCost;
import com.arc.mpl.project.RuntimeHelperPlanner;
import com.arc.mpl.project.RuntimePlanner;
import com.arc.mpl.project.RuntimePreferences;
import com.arc.mpl.project.RuntimeTopologyPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds the first automatic helper topology without coupling project loading to target emission. */
final class HelperShardCompiler {
    Optional<Result> compile(HirProgram program, HirEffectAnalyzer.Analysis effects,
                             String baselineMlog, String mainMil, TargetProfile profile,
                             RuntimePreferences preferences, PhysicalMemoryLayout baseMemory,
                             MlogLabelStyle labelStyle, List<HardwareRequirement> hardwareRequirements,
                             Map<String, RuntimeHelperCost> functionCosts) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(effects, "effects");
        RuntimeHelperPlan helpers = new RuntimeHelperPlanner().plan(
            program, effects, baselineMlog, profile, preferences, functionCosts);
        if (!helpers.enabled()) return Optional.empty();

        List<RuntimePlanner.ShardSource> seeds = new ArrayList<>();
        seeds.add(new RuntimePlanner.ShardSource("Main", List.of("main", "io"), baselineMlog));
        helpers.workers().forEach(worker -> seeds.add(new RuntimePlanner.ShardSource(
            worker.id(), List.of("worker", "numeric-helper"), String.join("\n", worker.functions()))));
        RuntimePlanner planner = new RuntimePlanner();
        SharedRuntimeLayoutPlanner.Result prepared = planner.prepareSharedRuntime(
            seeds, profile, preferences, baseMemory, helpers.mailboxRequirements());

        List<MultiShardCompilation.Shard> shards = new ArrayList<>();
        String mainMlog = generator("Main", prepared, helpers, profile, labelStyle, hardwareRequirements)
            .generate(program);
        shards.add(new MultiShardCompilation.Shard("Main", List.of("main", "io"), mainMlog, mainMil));
        for (RuntimeHelperPlan.Worker worker : helpers.workers()) {
            String workerMlog = generator(worker.id(), prepared, helpers, profile, labelStyle, List.of())
                .generate(program);
            String workerMil = new MilCodeGenerator().generate(workerProgram(program, worker));
            shards.add(new MultiShardCompilation.Shard(worker.id(), List.of("worker", "numeric-helper"),
                workerMlog, workerMil));
        }
        validateWorkerLimits(shards, helpers, profile);
        List<RuntimePlanner.ShardSource> generated = shards.stream().map(shard ->
            new RuntimePlanner.ShardSource(shard.id(), shard.roles(), shard.mlog())).toList();
        RuntimeTopologyPlan topology = planner.planTopology(generated, profile, preferences, prepared);
        topology = new RuntimeTopologyPlan(topology.shards(), topology.physicalMemoryLayout(),
            topology.sharedRuntime(), Optional.of(helpers));
        return Optional.of(new Result(new MultiShardCompilation(shards, topology, helpers),
            prepared.physicalMemoryLayout()));
    }

    private MlogCodeGenerator generator(String shard, SharedRuntimeLayoutPlanner.Result prepared,
                                        RuntimeHelperPlan helpers, TargetProfile profile,
                                        MlogLabelStyle labelStyle,
                                        List<HardwareRequirement> hardwareRequirements) {
        return new MlogCodeGenerator(labelStyle, prepared.physicalMemoryLayout(), hardwareRequirements,
            profile.capabilities(), MlogRuntimeContext.shared(shard, prepared.sharedRuntime(), helpers));
    }

    private HirProgram workerProgram(HirProgram program, RuntimeHelperPlan.Worker worker) {
        Map<String, HirFunction> functions = program.functions().stream().collect(java.util.stream.Collectors.toMap(
            HirFunction::name, value -> value, (left, right) -> left, LinkedHashMap::new));
        return new HirProgram(List.of(), worker.functions().stream().map(functions::get).toList(), List.of());
    }

    private void validateWorkerLimits(List<MultiShardCompilation.Shard> shards, RuntimeHelperPlan helpers,
                                      TargetProfile profile) {
        for (RuntimeHelperPlan.Worker worker : helpers.workers()) {
            MultiShardCompilation.Shard shard = shards.stream().filter(value -> value.id().equals(worker.id()))
                .findFirst().orElseThrow();
            MlogProgramMetrics metrics = MlogProgramMetrics.analyze(shard.mlog());
            List<String> violations = new ArrayList<>();
            if (metrics.instructions() > profile.maxInstructions()) {
                violations.add(metrics.instructions() + "/" + profile.maxInstructions() + " 指令");
            }
            if (metrics.labels() > profile.maxJumpLabels()) {
                violations.add(metrics.labels() + "/" + profile.maxJumpLabels() + " 标签");
            }
            if (metrics.maxTokensPerStatement() > profile.maxTokensPerStatement()) {
                violations.add(metrics.maxTokensPerStatement() + "/" + profile.maxTokensPerStatement() + " token/行");
            }
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("helper " + worker.id() + " 的函数 " + worker.functions()
                    + " 超出 target " + profile.id() + "：" + String.join("，", violations)
                    + "；ABI v2 不支持拆开存在调用依赖的函数，请增加可用处理器或缩小该函数分量");
            }
        }
    }

    record Result(MultiShardCompilation compilation, PhysicalMemoryLayout memoryLayout) {
        Result {
            Objects.requireNonNull(compilation, "compilation");
            Objects.requireNonNull(memoryLayout, "memoryLayout");
        }
    }
}
