package com.arc.mpl.ast;

import java.util.List;
import java.util.Objects;

/** Parsed MPL source before name or type analysis. */
public record Program(List<Statement> statements) {
    public Program {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }
}
