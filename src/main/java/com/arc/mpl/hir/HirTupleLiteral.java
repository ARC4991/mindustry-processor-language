package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Fixed-width tuple literal before static-slot lowering. */
public record HirTupleLiteral(List<HirExpression> elements, TupleType type) implements HirExpression {
    public HirTupleLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        Objects.requireNonNull(type, "type");
        if (!type.elementTypes().equals(elements.stream().map(HirExpression::type).toList())) {
            throw new IllegalArgumentException("tuple type must match literal element types");
        }
    }
}
