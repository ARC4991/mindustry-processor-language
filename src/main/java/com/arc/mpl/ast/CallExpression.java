package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** A call to a syntactically resolved callee expression. */
public record CallExpression(Expression callee, List<Expression> arguments, SourceSpan span) implements Expression {
    public CallExpression {
        Objects.requireNonNull(callee, "callee");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(span, "span");
    }
}
