package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record FunctionDeclaration(String name, List<FunctionParameter> parameters, Optional<String> returnType,
                                  BlockStatement body, SourceSpan span) {
    public FunctionDeclaration {
        Objects.requireNonNull(name, "name");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        returnType = Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
