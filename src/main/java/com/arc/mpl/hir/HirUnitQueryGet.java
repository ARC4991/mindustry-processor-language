package com.arc.mpl.hir;

import java.util.Objects;

/** A weakly consistent Unit query scan that returns one nullable, persistent UnitRef. */
public record HirUnitQueryGet(HirUnitQuery query, HirExpression index) implements HirExpression {
    public HirUnitQueryGet {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(index, "index");
    }

    @Override
    public UnitType type() {
        return new UnitType(query.unitType(), true);
    }
}
