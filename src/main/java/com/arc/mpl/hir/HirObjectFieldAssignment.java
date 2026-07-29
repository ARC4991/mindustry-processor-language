package com.arc.mpl.hir;

import java.util.Objects;

public record HirObjectFieldAssignment(HirExpression target, String className, String field, String operator,
                                       HirExpression value, MplType type) implements HirExpression {
    public HirObjectFieldAssignment {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(type, "type");
    }
}
