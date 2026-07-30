package com.arc.mpl.hir;

import java.util.Objects;

/** Captures one String argument value before later call arguments are evaluated. */
public record HirStringSnapshot(int allocationId, HirExpression value,
                                int maxCodeUnits) implements HirExpression {
    public HirStringSnapshot {
        if (allocationId < 1) throw new IllegalArgumentException("String 快照 allocationId 必须为正数");
        Objects.requireNonNull(value, "value");
        if (value.type() != ValueType.STRING) throw new IllegalArgumentException("String 快照只能捕获 String");
        if (maxCodeUnits < 0) throw new IllegalArgumentException("String 快照上界不能为负数");
    }

    @Override public ValueType type() { return ValueType.STRING; }
}
