package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record BooleanLiteral(boolean value, SourceSpan span) implements Expression {
    public BooleanLiteral {
        Objects.requireNonNull(span, "span");
    }
}
