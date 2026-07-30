package com.arc.mpl.hir;

import java.util.Objects;

/** Resets a MutableList's runtime length without releasing its reserved capacity. */
public record HirMutableListClear(String target) implements HirStatement {
    public HirMutableListClear {
        Objects.requireNonNull(target, "target");
    }
}
