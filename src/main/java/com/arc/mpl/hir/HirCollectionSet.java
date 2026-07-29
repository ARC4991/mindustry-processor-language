package com.arc.mpl.hir;

import java.util.Objects;

/** Compile-time-indexed mutable array element update. */
public record HirCollectionSet(String target, int index, HirExpression value) implements HirStatement {
    public HirCollectionSet {
        Objects.requireNonNull(target, "target");
        if (index < 0) throw new IllegalArgumentException("array index must be non-negative");
        Objects.requireNonNull(value, "value");
    }
}
