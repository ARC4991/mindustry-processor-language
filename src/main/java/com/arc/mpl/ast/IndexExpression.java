package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** Postfix collection or tuple element access. */
public record IndexExpression(Expression target, Expression index, SourceSpan span) implements Expression {
    public IndexExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(span, "span");
    }
}
