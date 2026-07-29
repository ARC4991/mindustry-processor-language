package com.arc.mpl.hir;

import java.util.Objects;

/** Runtime-indexed mutable Array write awaiting physical-Memory lowering. */
public record HirDynamicCollectionSet(String target, HirExpression index, HirExpression value) implements HirStatement {
    public HirDynamicCollectionSet {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(value, "value");
    }
}
