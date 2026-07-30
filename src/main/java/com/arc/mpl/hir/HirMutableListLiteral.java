package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A fixed-capacity, runtime-sized list backed by compiler-managed physical Memory. */
public record HirMutableListLiteral(List<HirExpression> elements, int capacity, CollectionType type)
    implements HirExpression {
    public HirMutableListLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        Objects.requireNonNull(type, "type");
        if (type.kind() != CollectionType.Kind.MUTABLE_LIST) {
            throw new IllegalArgumentException("mutable list literal requires MutableList type");
        }
        if (capacity < elements.size()) {
            throw new IllegalArgumentException("mutable list capacity cannot be below initial size");
        }
    }
}
