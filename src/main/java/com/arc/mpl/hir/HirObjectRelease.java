package com.arc.mpl.hir;

import java.util.Objects;

/** Compiler-inserted release of one uniquely owned physical object-pool handle. */
public record HirObjectRelease(String variable, String className) implements HirStatement {
    public HirObjectRelease {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(className, "className");
    }
}
