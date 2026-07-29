package com.arc.mpl.hir;

import java.util.Objects;

public record HirVariable(String name, MplType type) implements HirExpression {
    public HirVariable {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
