package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record FloatLiteral(double value, SourceSpan span) implements Expression {
    public FloatLiteral {
        Objects.requireNonNull(span, "span");
    }
}
