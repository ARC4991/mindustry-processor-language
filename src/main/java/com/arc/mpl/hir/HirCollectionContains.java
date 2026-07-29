package com.arc.mpl.hir;

import java.util.Objects;

/** Membership test over a statically laid-out aggregate. */
public record HirCollectionContains(HirExpression target, HirExpression candidate, int size) implements HirExpression {
    public HirCollectionContains {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidate, "candidate");
        if (size < 0) throw new IllegalArgumentException("collection size must be non-negative");
    }

    @Override
    public ValueType type() {
        return ValueType.BOOL;
    }
}
