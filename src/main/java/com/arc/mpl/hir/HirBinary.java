package com.arc.mpl.hir;

import java.util.Objects;

public record HirBinary(HirExpression left, String operator, HirExpression right, MplType type) implements HirExpression {
    public HirBinary {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(type, "type");
    }
}
