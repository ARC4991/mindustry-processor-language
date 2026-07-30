package com.arc.mpl.hir;

import java.util.Objects;

/** Appends one value to a compiler-capacity-checked MutableList. */
public record HirMutableListAdd(String target, HirExpression value) implements HirStatement {
    public HirMutableListAdd {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");
    }
}
