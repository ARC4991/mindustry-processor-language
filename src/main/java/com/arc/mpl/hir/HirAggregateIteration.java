package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Compile-time-bounded traversal of a statically laid-out aggregate variable. */
public record HirAggregateIteration(String bindingName, HirVariable source, MplType elementType, int size,
                                    List<HirStatement> body) implements HirStatement {
    public HirAggregateIteration {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(elementType, "elementType");
        if (size < 0) throw new IllegalArgumentException("aggregate size must be non-negative");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }
}
