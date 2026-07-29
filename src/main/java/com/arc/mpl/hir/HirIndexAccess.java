package com.arc.mpl.hir;

import java.util.Objects;

/** Indexed read from a statically laid-out tuple or collection. */
public record HirIndexAccess(HirExpression target, HirExpression index, MplType type) implements HirExpression {
    public HirIndexAccess {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(type, "type");
    }
}
