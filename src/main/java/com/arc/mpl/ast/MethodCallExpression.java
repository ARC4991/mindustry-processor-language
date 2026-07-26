package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import java.util.List;
import java.util.Objects;

public record MethodCallExpression(String target, String method, List<Expression> arguments, SourceSpan span) implements Expression {
    public MethodCallExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(method, "method");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(span, "span");
    }
}
