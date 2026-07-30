package com.arc.mpl.hir;

import java.util.Objects;

/** Removes one element and shifts the following MutableList values left. */
public record HirMutableListRemoveAt(String target, int index) implements HirStatement {
    public HirMutableListRemoveAt {
        Objects.requireNonNull(target, "target");
        if (index < 0) throw new IllegalArgumentException("MutableList index must be non-negative");
    }
}
