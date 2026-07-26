package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record UnaryExpression(String operator, Expression operand, SourceSpan span) implements Expression {
    public UnaryExpression {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(span, "span");
    }
}
