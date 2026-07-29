package com.arc.mpl.project;

import com.arc.mpl.ast.Program;
import com.arc.mpl.diagnostic.Diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Linked project-local module program and its deterministic module order. */
public record ProjectProgramResult(Optional<Program> program, List<Diagnostic> diagnostics, List<Path> modules) {
    public ProjectProgramResult {
        program = program == null ? Optional.empty() : program;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
    }

    public boolean succeeded() {
        return program.isPresent() && diagnostics.isEmpty();
    }
}
