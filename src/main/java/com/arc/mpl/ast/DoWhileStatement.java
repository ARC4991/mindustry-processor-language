package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** Source-level post-tested loop. */
public record DoWhileStatement(BlockStatement body, Expression condition, SourceSpan span) implements Statement {
    public DoWhileStatement {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(span, "span");
    }
}
