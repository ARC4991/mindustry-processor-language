package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record ExpressionStatement(Expression expression, SourceSpan span) implements Statement {
    public ExpressionStatement {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }
}
