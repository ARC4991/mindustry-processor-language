package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record ClassFieldDeclaration(AccessModifier access, String name, String typeName, SourceSpan span) {
    public ClassFieldDeclaration {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(span, "span");
    }
}
