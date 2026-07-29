package com.arc.mpl.ast;

import java.util.Objects;

public record ClassMethodDeclaration(AccessModifier access, FunctionDeclaration function) {
    public ClassMethodDeclaration {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(function, "function");
    }
}
