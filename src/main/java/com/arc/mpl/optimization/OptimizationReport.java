package com.arc.mpl.optimization;

/** Immutable, machine-readable summary of target-neutral HIR optimizations. */
public record OptimizationReport(int constantFolds, int eliminatedBranches, int eliminatedLoops,
                                 int eliminatedStatements) {
    public static final OptimizationReport NONE = new OptimizationReport(0, 0, 0, 0);

    public OptimizationReport {
        if (constantFolds < 0 || eliminatedBranches < 0 || eliminatedLoops < 0 || eliminatedStatements < 0) {
            throw new IllegalArgumentException("optimization counters must not be negative");
        }
    }

    public boolean changedProgram() {
        return constantFolds != 0 || eliminatedBranches != 0 || eliminatedLoops != 0 || eliminatedStatements != 0;
    }
}
