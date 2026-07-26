package com.arc.mpl.hir;

import java.util.Objects;

public record HirExpressionStatement(HirExpression expression) implements HirStatement {
    public HirExpressionStatement {
        Objects.requireNonNull(expression, "expression");
    }
}
