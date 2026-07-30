package com.arc.mpl.project;

import com.arc.mpl.codegen.MlogProgramMetrics;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.profile.TargetProfile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Selects the first conservative automatic multi-processor boundary: pure numeric functions. */
public final class RuntimeHelperPlanner {
    public RuntimeHelperPlan plan(HirProgram program, HirEffectAnalyzer.Analysis effects,
                                  String baselineMlog, TargetProfile profile,
                                  RuntimePreferences preferences) {
        Map<String, RuntimeHelperCost> fallback = new LinkedHashMap<>();
        program.functions().forEach(function -> fallback.put(function.name(), RuntimeHelperCost.unit()));
        return plan(program, effects, baselineMlog, profile, preferences, fallback);
    }

    public RuntimeHelperPlan plan(HirProgram program, HirEffectAnalyzer.Analysis effects,
                                  String baselineMlog, TargetProfile profile,
                                  RuntimePreferences preferences, Map<String, RuntimeHelperCost> functionCosts) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(baselineMlog, "baselineMlog");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");
        functionCosts = Map.copyOf(Objects.requireNonNull(functionCosts, "functionCosts"));
        if (availableProcessors(preferences) < 2) return RuntimeHelperPlan.empty();

        int instructions = MlogProgramMetrics.analyze(baselineMlog).instructions();
        if (!shouldUseWorker(preferences.goal(), instructions, profile.maxInstructions())) {
            return RuntimeHelperPlan.empty();
        }
        List<HirFunction> candidates = program.functions().stream()
            .filter(function -> {
                HirEffectAnalyzer.FunctionEffect effect = effects.function(function.name());
                return effects.reachable(function.name()) && effect != null && effect.pureNumeric();
            }).toList();
        if (candidates.isEmpty()) return RuntimeHelperPlan.empty();
        List<List<HirFunction>> components = dependencyComponents(candidates, effects);
        int workerLimit = Math.toIntExact(Math.min(components.size(), availableProcessors(preferences) - 1));
        Map<String, RuntimeHelperCost> candidateCosts = new LinkedHashMap<>();
        for (HirFunction candidate : candidates) {
            RuntimeHelperCost cost = functionCosts.get(candidate.name());
            if (cost == null) throw new IllegalArgumentException("纯数值 helper 缺少目标成本：" + candidate.name());
            candidateCosts.put(candidate.name(), cost);
        }
        return RuntimeHelperPlan.partitioned(candidates, balance(components, workerLimit, candidateCosts),
            candidateCosts);
    }

    private boolean shouldUseWorker(RuntimePreferences.Goal goal, int instructions, int maximum) {
        return switch (goal) {
            case MIN_RESOURCES -> instructions > maximum;
            case BALANCED -> instructions >= Math.multiplyExact(maximum, 3) / 4;
            case MAX_PERFORMANCE -> true;
        };
    }

    private long availableProcessors(RuntimePreferences preferences) {
        return preferences.processors().values().stream().filter(value -> value > 0)
            .mapToLong(Integer::longValue).sum();
    }

    /** Keeps callers and callees together because Worker-to-Worker calls are not part of ABI v2. */
    private List<List<HirFunction>> dependencyComponents(List<HirFunction> candidates,
                                                         HirEffectAnalyzer.Analysis effects) {
        Map<String, HirFunction> byName = new LinkedHashMap<>();
        candidates.forEach(function -> byName.put(function.name(), function));
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        byName.keySet().forEach(name -> adjacency.put(name, new LinkedHashSet<>()));
        for (String caller : byName.keySet()) {
            for (String callee : effects.callees(caller)) {
                if (!byName.containsKey(callee)) continue;
                adjacency.get(caller).add(callee);
                adjacency.get(callee).add(caller);
            }
        }
        List<List<HirFunction>> components = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String first : byName.keySet()) {
            if (!visited.add(first)) continue;
            Set<String> members = new LinkedHashSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(first);
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                members.add(current);
                for (String adjacent : adjacency.get(current)) {
                    if (visited.add(adjacent)) pending.addLast(adjacent);
                }
            }
            components.add(candidates.stream().filter(function -> members.contains(function.name())).toList());
        }
        return List.copyOf(components);
    }

    /** Stable longest-component-first balancing; function order inside each Worker remains source order. */
    private List<List<HirFunction>> balance(List<List<HirFunction>> components, int workerCount,
                                            Map<String, RuntimeHelperCost> costs) {
        if (workerCount < 1) throw new IllegalArgumentException("helper Worker 数量必须大于 0");
        List<List<HirFunction>> buckets = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) buckets.add(new ArrayList<>());
        List<IndexedComponent> ordered = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            ordered.add(new IndexedComponent(index, components.get(index)));
        }
        ordered.sort(Comparator.comparingInt((IndexedComponent value) -> componentCost(value.functions(), costs)
                .instructions()).reversed()
            .thenComparing(Comparator.comparingInt((IndexedComponent value) -> componentCost(value.functions(), costs)
                .labels()).reversed())
            .thenComparingInt(IndexedComponent::sourceIndex));
        for (IndexedComponent component : ordered) {
            int target = 0;
            for (int index = 1; index < buckets.size(); index++) {
                if (predictedCost(buckets.get(index), component.functions(), costs)
                    .compareTo(predictedCost(buckets.get(target), component.functions(), costs)) < 0) target = index;
            }
            buckets.get(target).addAll(component.functions());
        }
        Map<String, Integer> sourceOrder = new LinkedHashMap<>();
        int order = 0;
        for (List<HirFunction> component : components) {
            for (HirFunction function : component) sourceOrder.put(function.name(), order++);
        }
        buckets.forEach(bucket -> bucket.sort(Comparator.comparingInt(function -> sourceOrder.get(function.name()))));
        return buckets.stream().map(List::copyOf).toList();
    }

    private RuntimeHelperCost componentCost(List<HirFunction> functions, Map<String, RuntimeHelperCost> costs) {
        int instructions = 0;
        int labels = 0;
        for (HirFunction function : functions) {
            RuntimeHelperCost cost = costs.get(function.name());
            instructions = Math.addExact(instructions, cost.instructions() + 2);
            labels = Math.addExact(labels, cost.labels() + 1);
        }
        return new RuntimeHelperCost(instructions, labels);
    }

    private PredictedCost predictedCost(List<HirFunction> current, List<HirFunction> added,
                                        Map<String, RuntimeHelperCost> costs) {
        List<HirFunction> combined = new ArrayList<>(current);
        combined.addAll(added);
        RuntimeHelperCost body = componentCost(combined, costs);
        int payloadWidth = combined.stream().mapToInt(function -> function.parameters().size()).max().orElse(0);
        return new PredictedCost(Math.addExact(body.instructions(), Math.multiplyExact(payloadWidth, 3)),
            body.labels(), combined.size());
    }

    private record IndexedComponent(int sourceIndex, List<HirFunction> functions) {
    }

    private record PredictedCost(int instructions, int labels, int functions) implements Comparable<PredictedCost> {
        @Override
        public int compareTo(PredictedCost other) {
            int instructionOrder = Integer.compare(instructions, other.instructions);
            if (instructionOrder != 0) return instructionOrder;
            int labelOrder = Integer.compare(labels, other.labels);
            if (labelOrder != 0) return labelOrder;
            return Integer.compare(functions, other.functions);
        }
    }
}
