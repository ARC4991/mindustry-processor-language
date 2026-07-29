package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A MIL macro invocation that owns a structured body. */
public record MilMacroBlockStatement(MilMacroCallExpression macro, BlockStatement body, SourceSpan span)
    implements Statement {
    public MilMacroBlockStatement {
        Objects.requireNonNull(macro, "macro");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
