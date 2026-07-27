package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A generic for-each source form; the semantic layer currently recognizes UnitSet queries. */
public record ForEachStatement(String name, Expression iterable, BlockStatement body, SourceSpan span) implements Statement {
    public ForEachStatement {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(iterable, "iterable");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
