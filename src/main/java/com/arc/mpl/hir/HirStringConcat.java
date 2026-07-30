package com.arc.mpl.hir;

import java.util.Objects;

/** One bounded immutable String concatenation backed by compiler-owned storage. */
public record HirStringConcat(int allocationId, HirExpression left, HirExpression right,
                              int maxCodeUnits) implements HirExpression {
    public HirStringConcat {
        if (allocationId < 1) throw new IllegalArgumentException("String allocationId 必须为正数");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.type() != ValueType.STRING || right.type() != ValueType.STRING) {
            throw new IllegalArgumentException("String 拼接两侧必须都是 String");
        }
        if (maxCodeUnits < 0) throw new IllegalArgumentException("String 上界不能为负数");
    }

    @Override public ValueType type() { return ValueType.STRING; }
}
