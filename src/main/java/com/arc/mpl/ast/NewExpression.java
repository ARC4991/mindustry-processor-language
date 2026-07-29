package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

public record NewExpression(String className, List<Expression> arguments, SourceSpan span) implements Expression {
    public NewExpression {
        Objects.requireNonNull(className, "className");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(span, "span");
    }
}
