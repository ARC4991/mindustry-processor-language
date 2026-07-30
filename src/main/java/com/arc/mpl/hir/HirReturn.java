package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A function return plus compiler-owned cleanup that runs after evaluating its value. */
public record HirReturn(Optional<HirExpression> value, List<HirObjectRelease> cleanup) implements HirStatement {
    public HirReturn {
        value = Objects.requireNonNull(value, "value");
        cleanup = List.copyOf(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public HirReturn(Optional<HirExpression> value) {
        this(value, List.of());
    }
}
