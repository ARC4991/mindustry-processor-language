package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.optimization.OptimizationReport;
import com.arc.mpl.optimization.HirEffectAnalyzer;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result boundary between the compiler pipeline and CLI/UI clients. */
public record CompilationResult(
    Optional<TargetProfile> profile,
    List<Diagnostic> diagnostics,
    Optional<String> mlog,
    Optional<String> mil,
    OptimizationReport optimizationReport,
    PhysicalMemoryLayout physicalMemoryLayout,
    HirEffectAnalyzer.Analysis effectAnalysis
) {
    public CompilationResult {
        profile = profile == null ? Optional.empty() : profile;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        mlog = mlog == null ? Optional.empty() : mlog;
        mil = mil == null ? Optional.empty() : mil;
        optimizationReport = optimizationReport == null ? OptimizationReport.NONE : optimizationReport;
        physicalMemoryLayout = physicalMemoryLayout == null ? PhysicalMemoryLayout.empty() : physicalMemoryLayout;
        effectAnalysis = effectAnalysis == null ? HirEffectAnalyzer.Analysis.empty() : effectAnalysis;
    }

    public CompilationResult(
        Optional<TargetProfile> profile,
        List<Diagnostic> diagnostics,
        Optional<String> mlog,
        Optional<String> mil,
        OptimizationReport optimizationReport
    ) {
        this(profile, diagnostics, mlog, mil, optimizationReport, PhysicalMemoryLayout.empty(), HirEffectAnalyzer.Analysis.empty());
    }

    /**
     * Compatibility constructor for callers that only receive a final mlog
     * artifact. Successful compiler builds should use the four-argument
     * constructor so the inspectable MIL artifact travels with it.
     */
    public CompilationResult(
        Optional<TargetProfile> profile,
        List<Diagnostic> diagnostics,
        Optional<String> mlog,
        Optional<String> mil
    ) {
        this(profile, diagnostics, mlog, mil, OptimizationReport.NONE, PhysicalMemoryLayout.empty(), HirEffectAnalyzer.Analysis.empty());
    }

    /** Compatibility constructor for callers that only receive a final mlog artifact. */
    public CompilationResult(
        Optional<TargetProfile> profile,
        List<Diagnostic> diagnostics,
        Optional<String> mlog
    ) {
        this(profile, diagnostics, mlog, Optional.empty(), OptimizationReport.NONE, PhysicalMemoryLayout.empty(), HirEffectAnalyzer.Analysis.empty());
    }

    public boolean succeeded() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
