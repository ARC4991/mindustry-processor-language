package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A deliberately small single-parameter lambda syntax node. */
public record LambdaExpression(String parameter, Expression body, SourceSpan span) implements Expression {
    public LambdaExpression {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
