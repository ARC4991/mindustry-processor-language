package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/** Structured conditional with an optional alternative branch. */
public record IfStatement(Expression condition, BlockStatement thenBlock, Optional<BlockStatement> elseBlock,
                          SourceSpan span) implements Statement {
    public IfStatement {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenBlock, "thenBlock");
        elseBlock = Objects.requireNonNull(elseBlock, "elseBlock");
        Objects.requireNonNull(span, "span");
    }
}
