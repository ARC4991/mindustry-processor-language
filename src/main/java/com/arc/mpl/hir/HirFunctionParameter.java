package com.arc.mpl.hir;

import java.util.Objects;

public record HirFunctionParameter(String name, MplType type) {
    public HirFunctionParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
