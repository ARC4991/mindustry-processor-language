package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** List or Set literal after the source factory call has been resolved. */
public record HirCollectionLiteral(List<HirExpression> elements, CollectionType type) implements HirExpression {
    public HirCollectionLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        Objects.requireNonNull(type, "type");
        if (type.kind() == CollectionType.Kind.ARRAY) {
            throw new IllegalArgumentException("use HirArrayLiteral for array literals");
        }
        if (type.kind() == CollectionType.Kind.MUTABLE_LIST) {
            throw new IllegalArgumentException("use HirMutableListLiteral for mutable list literals");
        }
    }
}
