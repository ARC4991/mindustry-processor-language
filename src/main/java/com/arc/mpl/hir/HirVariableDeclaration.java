package com.arc.mpl.hir;

import java.util.Objects;

/** A source-level declaration retained for structured MIL serialization. */
public record HirVariableDeclaration(
    String name,
    MplType type,
    boolean mutable,
    HirExpression initializer,
    boolean ownsPooledObject,
    int stringCapacity
) implements HirStatement {
    public HirVariableDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
        if (stringCapacity < 0 || type != ValueType.STRING && stringCapacity != 0) {
            throw new IllegalArgumentException("stringCapacity 只适用于 String 且不能为负数");
        }
    }

    /**
     * Compatibility constructor for existing lowering tests. New front-end
     * callers must preserve whether the declaration was {@code var} or
     * {@code val}.
     */
    public HirVariableDeclaration(String name, MplType type, HirExpression initializer) {
        this(name, type, true, initializer, false, 0);
    }

    public HirVariableDeclaration(String name, MplType type, boolean mutable, HirExpression initializer) {
        this(name, type, mutable, initializer, false, 0);
    }

    public HirVariableDeclaration(String name, MplType type, boolean mutable, HirExpression initializer,
                                  boolean ownsPooledObject) {
        this(name, type, mutable, initializer, ownsPooledObject, 0);
    }
}
