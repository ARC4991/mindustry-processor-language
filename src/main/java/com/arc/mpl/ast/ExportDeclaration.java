package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** Marks one top-level function or value as visible to importing modules. */
public record ExportDeclaration(String name, SourceSpan span) {
    public ExportDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }
}
