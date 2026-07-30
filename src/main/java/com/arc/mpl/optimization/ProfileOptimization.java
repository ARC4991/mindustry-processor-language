package com.arc.mpl.optimization;

/** One profile-specific lowering applied after target-neutral HIR optimization. */
public record ProfileOptimization(String name, int applied, int estimatedInstructionsSaved,
                                  int estimatedLabelsSaved) {
    public ProfileOptimization {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("profile optimization name is required");
        if (applied < 0 || estimatedInstructionsSaved < 0 || estimatedLabelsSaved < 0) {
            throw new IllegalArgumentException("profile optimization counters must not be negative");
        }
    }
}
