package com.arc.mpl.hir;

import java.util.Objects;

/** Runtime count of the units currently accepted by a lazy Unit query. */
public record HirUnitQuerySize(HirUnitQuery query) implements HirExpression {
    public HirUnitQuerySize {
        Objects.requireNonNull(query, "query");
    }

    @Override
    public ValueType type() {
        return ValueType.INT;
    }
}
