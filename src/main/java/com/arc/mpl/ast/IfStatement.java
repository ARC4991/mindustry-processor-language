package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/** Structured conditional with an optional alternative branch. */
public record IfStatement(Expression condition, BlockStatement thenBlock, Optional<Statement> elseBranch,
                          SourceSpan span) implements Statement {
    public IfStatement {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenBlock, "thenBlock");
        elseBranch = Objects.requireNonNull(elseBranch, "elseBranch");
        Objects.requireNonNull(span, "span");
    }
}
