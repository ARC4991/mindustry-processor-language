package com.arc.mpl.hir;

import java.util.Objects;

public record HirAssignment(String target, String operator, HirExpression value, ValueType type) implements HirExpression {
    public HirAssignment {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(type, "type");
    }
}
