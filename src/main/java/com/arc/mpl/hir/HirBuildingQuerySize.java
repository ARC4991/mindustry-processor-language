package com.arc.mpl.hir;

import java.util.Objects;

/** Runtime count of linked buildings currently accepted by a lazy query. */
public record HirBuildingQuerySize(HirBuildingQuery query) implements HirExpression {
    public HirBuildingQuerySize {
        Objects.requireNonNull(query, "query");
    }

    @Override
    public ValueType type() {
        return ValueType.INT;
    }
}
