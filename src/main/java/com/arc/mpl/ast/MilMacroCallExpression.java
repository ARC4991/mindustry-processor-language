package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** A profile-whitelisted MIL macro before semantic lowering. */
public record MilMacroCallExpression(String name, List<Expression> arguments, SourceSpan span) implements Expression {
    public MilMacroCallExpression {
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(span, "span");
    }
}
