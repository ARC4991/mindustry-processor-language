package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record Identifier(String name, SourceSpan span) implements Expression {
    public Identifier {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}
