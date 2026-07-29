package com.arc.mpl.hir;

import java.util.Objects;

/** A source-level declaration retained for structured MIL serialization. */
public record HirVariableDeclaration(
    String name,
    MplType type,
    boolean mutable,
    HirExpression initializer
) implements HirStatement {
    public HirVariableDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
    }

    /**
     * Compatibility constructor for existing lowering tests. New front-end
     * callers must preserve whether the declaration was {@code var} or
     * {@code val}.
     */
    public HirVariableDeclaration(String name, MplType type, HirExpression initializer) {
        this(name, type, true, initializer);
    }
}
