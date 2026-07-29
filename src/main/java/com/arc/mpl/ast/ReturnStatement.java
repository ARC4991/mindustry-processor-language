package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;
import java.util.Optional;

public record ReturnStatement(Optional<Expression> value, SourceSpan span) implements Statement {
    public ReturnStatement {
        value = Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}
