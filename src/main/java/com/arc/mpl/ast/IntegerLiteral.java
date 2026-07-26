package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record IntegerLiteral(long value, SourceSpan span) implements Expression {
    public IntegerLiteral {
        Objects.requireNonNull(span, "span");
    }
}
