package com.arc.mpl.hir;

import java.util.Objects;

/** UTF-16 code-unit length of one runtime String value. */
public record HirStringLength(HirExpression value) implements HirExpression {
    public HirStringLength {
        Objects.requireNonNull(value, "value");
        if (value.type() != ValueType.STRING) throw new IllegalArgumentException("length 目标必须是 String");
    }

    @Override public ValueType type() { return ValueType.INT; }
}
