package com.arc.mpl.hir;

import java.util.Objects;

public record HirConstant(String mlogLiteral, ValueType type) implements HirExpression {
    public HirConstant {
        Objects.requireNonNull(mlogLiteral, "mlogLiteral");
        Objects.requireNonNull(type, "type");
    }
}
