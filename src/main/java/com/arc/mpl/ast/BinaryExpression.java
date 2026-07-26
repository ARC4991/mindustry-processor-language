package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record BinaryExpression(Expression left, String operator, Expression right, SourceSpan span) implements Expression {
    public BinaryExpression {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(span, "span");
    }
}
