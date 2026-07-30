package com.arc.mpl.project;

import com.arc.mpl.codegen.MlogProgramMetrics;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.profile.TargetProfile;

import java.util.List;
import java.util.Objects;

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
        return RuntimeHelperPlan.singleWorker(candidates);
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
}
