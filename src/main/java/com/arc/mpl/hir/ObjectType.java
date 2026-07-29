package com.arc.mpl.hir;

import java.util.Objects;

/** A nominal user class reference represented by a compiler-private positive handle. */
public record ObjectType(String className, boolean nullable) implements MplType {
    public ObjectType {
        Objects.requireNonNull(className, "className");
        if (className.isBlank()) throw new IllegalArgumentException("className 不能为空");
    }

    @Override
    public boolean canAssignFrom(MplType value) {
        if (value == ValueType.ERROR) return true;
        if (nullable && value == ValueType.NULL) return true;
        return value instanceof ObjectType object && className.equals(object.className())
            && (nullable || !object.nullable());
    }

    @Override
    public String displayName() {
        return className + (nullable ? "?" : "");
    }
}
