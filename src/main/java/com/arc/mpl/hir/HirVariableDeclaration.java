package com.arc.mpl.hir;

import java.util.Objects;

public record HirVariableDeclaration(String name, ValueType type, HirExpression initializer) implements HirStatement {
    public HirVariableDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
    }
}
