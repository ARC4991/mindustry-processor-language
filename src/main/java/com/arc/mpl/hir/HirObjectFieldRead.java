package com.arc.mpl.hir;

import java.util.Objects;

public record HirObjectFieldRead(HirExpression target, String className, String field,
                                 MplType type) implements HirExpression {
    public HirObjectFieldRead {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(type, "type");
    }
}
