package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record BreakStatement(SourceSpan span) implements Statement {
    public BreakStatement { Objects.requireNonNull(span, "span"); }
}
