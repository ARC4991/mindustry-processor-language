package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;
import java.util.Optional;

public record VariableDeclaration(
    boolean mutable,
    String name,
    Optional<String> declaredType,
    Expression initializer,
    SourceSpan span
) implements Statement {
    public VariableDeclaration {
        Objects.requireNonNull(name, "name");
        declaredType = declaredType == null ? Optional.empty() : declaredType;
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(span, "span");
    }
}
