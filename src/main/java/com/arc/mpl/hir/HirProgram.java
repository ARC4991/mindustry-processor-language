package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirProgram(List<HirStatement> statements) {
    public HirProgram {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }
}
