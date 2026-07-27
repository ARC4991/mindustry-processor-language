package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** A lexical statement block. */
public record BlockStatement(List<Statement> statements, SourceSpan span) implements Statement {
    public BlockStatement {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
        Objects.requireNonNull(span, "span");
    }
}
