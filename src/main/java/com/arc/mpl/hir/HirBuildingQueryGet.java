package com.arc.mpl.hir;

import java.util.Objects;

/** Selects one nullable Building reference from a filtered linked-building query. */
public record HirBuildingQueryGet(HirBuildingQuery query, HirExpression index) implements HirExpression {
    public HirBuildingQueryGet {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(index, "index");
    }

    @Override
    public BuildingType type() {
        return new BuildingType(query.buildingType(), true);
    }
}
