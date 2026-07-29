package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

public record ClassDeclaration(String name, List<ClassFieldDeclaration> fields,
                               List<ClassMethodDeclaration> methods, SourceSpan span) {
    public ClassDeclaration {
        Objects.requireNonNull(name, "name");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        Objects.requireNonNull(span, "span");
    }
}
