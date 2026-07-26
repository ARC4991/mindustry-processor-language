package com.arc.mpl.hir;

import java.util.Objects;

public record HirUnary(String operator, HirExpression operand, ValueType type) implements HirExpression {
    public HirUnary {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(type, "type");
    }
}
