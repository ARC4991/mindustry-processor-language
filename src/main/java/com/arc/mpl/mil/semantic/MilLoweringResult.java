package com.arc.mpl.mil.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.diagnostic.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of converting validated MIL macros into the shared semantic AST. */
public record MilLoweringResult(Optional<Program> program, List<Diagnostic> diagnostics) {
    public MilLoweringResult {
        program = program == null ? Optional.empty() : program;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean succeeded() {
        return program.isPresent() && diagnostics.isEmpty();
    }
}
