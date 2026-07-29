package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record ContinueStatement(SourceSpan span) implements Statement {
    public ContinueStatement { Objects.requireNonNull(span, "span"); }
}
