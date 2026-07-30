package com.arc.mpl.optimization;

import java.util.List;
import java.util.Objects;

/** Immutable, machine-readable summary of target-neutral and profile-specific optimizations. */
public record OptimizationReport(int constantFolds, int eliminatedBranches, int eliminatedLoops,
                                 int eliminatedStatements, List<ProfileOptimization> profileOptimizations) {
    public static final OptimizationReport NONE = new OptimizationReport(0, 0, 0, 0, List.of());

    public OptimizationReport(int constantFolds, int eliminatedBranches, int eliminatedLoops,
                              int eliminatedStatements) {
        this(constantFolds, eliminatedBranches, eliminatedLoops, eliminatedStatements, List.of());
    }

    public OptimizationReport {
        if (constantFolds < 0 || eliminatedBranches < 0 || eliminatedLoops < 0 || eliminatedStatements < 0) {
            throw new IllegalArgumentException("optimization counters must not be negative");
        }
        profileOptimizations = List.copyOf(Objects.requireNonNull(profileOptimizations, "profileOptimizations"));
    }

    public OptimizationReport withProfileOptimization(ProfileOptimization optimization) {
        List<ProfileOptimization> values = new java.util.ArrayList<>(profileOptimizations);
        values.add(Objects.requireNonNull(optimization, "optimization"));
        return new OptimizationReport(constantFolds, eliminatedBranches, eliminatedLoops,
            eliminatedStatements, values);
    }

    public boolean changedProgram() {
        return constantFolds != 0 || eliminatedBranches != 0 || eliminatedLoops != 0 || eliminatedStatements != 0
            || !profileOptimizations.isEmpty();
    }
}
