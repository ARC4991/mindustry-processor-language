package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirProgram(List<HirFunction> functions, List<HirStatement> statements) {
    public HirProgram {
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }

    public HirProgram(List<HirStatement> statements) {
        this(List.of(), statements);
    }
}
