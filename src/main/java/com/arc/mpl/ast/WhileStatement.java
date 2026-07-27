package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A source-level while loop; lowering owns all generated labels. */
public record WhileStatement(Expression condition, BlockStatement body, SourceSpan span) implements Statement {
    public WhileStatement {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
