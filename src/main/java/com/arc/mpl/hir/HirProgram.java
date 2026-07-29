package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirProgram(List<HirClass> classes, List<HirFunction> functions, List<HirStatement> statements) {
    public HirProgram {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }

    public HirProgram(List<HirFunction> functions, List<HirStatement> statements) {
        this(List.of(), functions, statements);
    }

    public HirProgram(List<HirStatement> statements) {
        this(List.of(), List.of(), statements);
    }
}
