package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ClassDeclaration(String name, Optional<String> superClass, List<ClassFieldDeclaration> fields,
                               List<ClassMethodDeclaration> methods, SourceSpan span) {
    public ClassDeclaration {
        Objects.requireNonNull(name, "name");
        superClass = Objects.requireNonNull(superClass, "superClass");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        Objects.requireNonNull(span, "span");
    }

    public ClassDeclaration(String name, List<ClassFieldDeclaration> fields,
                            List<ClassMethodDeclaration> methods, SourceSpan span) {
        this(name, Optional.empty(), fields, methods, span);
    }
}
