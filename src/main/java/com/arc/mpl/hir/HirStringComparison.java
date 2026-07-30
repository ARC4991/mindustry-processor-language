package com.arc.mpl.hir;

import java.util.Objects;

/** UTF-16 sequence equality or inequality for two runtime String values. */
public record HirStringComparison(HirExpression left, HirExpression right, boolean equal)
    implements HirExpression {
    public HirStringComparison {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.type() != ValueType.STRING || right.type() != ValueType.STRING) {
            throw new IllegalArgumentException("String 比较两侧必须都是 String");
        }
    }

    @Override public ValueType type() { return ValueType.BOOL; }
}
