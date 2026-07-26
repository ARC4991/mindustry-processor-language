package com.arc.mpl.semantic;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.hir.HirProgram;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SemanticResult(Optional<HirProgram> program, List<Diagnostic> diagnostics) {
    public SemanticResult {
        program = program == null ? Optional.empty() : program;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
