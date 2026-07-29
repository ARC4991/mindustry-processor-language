package com.arc.mpl.hir;

import java.util.Objects;

/** Commits the current processor graphics buffer to one declared Display. */
public record HirDrawFlush(String displayName) implements HirStatement {
    public HirDrawFlush {
        Objects.requireNonNull(displayName, "displayName");
    }
}
