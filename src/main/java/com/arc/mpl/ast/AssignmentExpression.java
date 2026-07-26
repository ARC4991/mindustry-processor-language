package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record AssignmentExpression(Identifier target, String operator, Expression value, SourceSpan span) implements Expression {
    public AssignmentExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}
