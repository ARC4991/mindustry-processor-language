package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import java.util.Objects;

public record StringLiteral(String value, SourceSpan span) implements Expression {
    public StringLiteral {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}
