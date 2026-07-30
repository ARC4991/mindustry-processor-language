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
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(baselineMlog, "baselineMlog");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");
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
        return RuntimeHelperPlan.partitioned(candidates, balance(components, workerLimit));
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
    private List<List<HirFunction>> balance(List<List<HirFunction>> components, int workerCount) {
        if (workerCount < 1) throw new IllegalArgumentException("helper Worker 数量必须大于 0");
        List<List<HirFunction>> buckets = new ArrayList<>();
        for (int index = 0; index < workerCount; index++) buckets.add(new ArrayList<>());
        List<IndexedComponent> ordered = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            ordered.add(new IndexedComponent(index, components.get(index)));
        }
        ordered.sort(Comparator.comparingInt((IndexedComponent value) -> value.functions().size()).reversed()
            .thenComparingInt(IndexedComponent::sourceIndex));
        for (IndexedComponent component : ordered) {
            int target = 0;
            for (int index = 1; index < buckets.size(); index++) {
                if (buckets.get(index).size() < buckets.get(target).size()) target = index;
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

    private record IndexedComponent(int sourceIndex, List<HirFunction> functions) {
    }
}
