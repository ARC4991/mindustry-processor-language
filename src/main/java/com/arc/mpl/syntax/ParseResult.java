package com.arc.mpl.syntax;

import com.arc.mpl.ast.Program;
import com.arc.mpl.diagnostic.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of syntax analysis; a program exists only when parsing completed without errors. */
public record ParseResult(Optional<Program> program, List<Diagnostic> diagnostics) {
    public ParseResult {
        program = program == null ? Optional.empty() : program;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean succeeded() {
        return program.isPresent() && diagnostics.isEmpty();
    }
}
