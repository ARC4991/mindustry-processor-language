package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Homogeneous array literal before static-slot or Memory lowering. */
public record HirArrayLiteral(List<HirExpression> elements, CollectionType type) implements HirExpression {
    public HirArrayLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        Objects.requireNonNull(type, "type");
        if (type.kind() != CollectionType.Kind.ARRAY) throw new IllegalArgumentException("array literal requires array type");
    }
}
