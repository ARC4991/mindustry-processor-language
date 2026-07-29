package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A game-owned symbol such as {@code @dagger} or {@code @message1}. */
public record MilGameSymbolExpression(String name, SourceSpan span) implements Expression {
    public MilGameSymbolExpression {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}
