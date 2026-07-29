package com.arc.mpl.ast;

import java.util.List;
import java.util.Objects;

/** Parsed MPL source before name or type analysis. */
public record Program(List<FunctionDeclaration> functions, List<Statement> statements) {
    public Program {
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }

    public Program(List<Statement> statements) {
        this(List.of(), statements);
    }
}
