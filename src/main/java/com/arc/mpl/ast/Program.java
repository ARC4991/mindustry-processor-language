package com.arc.mpl.ast;

import java.util.List;
import java.util.Objects;

/** Parsed MPL source before name or type analysis. */
public record Program(
    List<ImportDeclaration> imports,
    List<ExportDeclaration> exports,
    List<FunctionDeclaration> functions,
    List<Statement> statements
) {
    public Program {
        imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
        exports = List.copyOf(Objects.requireNonNull(exports, "exports"));
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }

    public Program(List<FunctionDeclaration> functions, List<Statement> statements) {
        this(List.of(), List.of(), functions, statements);
    }

    public Program(List<Statement> statements) {
        this(List.of(), List.of(), List.of(), statements);
    }
}
