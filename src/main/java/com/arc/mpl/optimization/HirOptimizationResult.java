package com.arc.mpl.optimization;

import com.arc.mpl.hir.HirProgram;

import java.util.Objects;

/** Result boundary between semantic analysis and profile-specific lowering. */
public record HirOptimizationResult(HirProgram program, OptimizationReport report) {
    public HirOptimizationResult {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(report, "report");
    }
}
