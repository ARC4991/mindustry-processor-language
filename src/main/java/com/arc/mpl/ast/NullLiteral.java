package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** The sole source literal for an absent nullable object reference. */
public record NullLiteral(SourceSpan span) implements Expression {
    public NullLiteral {
        Objects.requireNonNull(span, "span");
    }
}
