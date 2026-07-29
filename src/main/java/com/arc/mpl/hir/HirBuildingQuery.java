package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A lazy descriptor over one type's statically declared hardware links. */
public record HirBuildingQuery(
    String bindingName,
    String buildingType,
    String mlogType,
    List<HirHardwareLink> buildings,
    List<HirExpression> filters
) implements HirExpression {
    public HirBuildingQuery {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(buildingType, "buildingType");
        Objects.requireNonNull(mlogType, "mlogType");
        buildings = List.copyOf(Objects.requireNonNull(buildings, "buildings"));
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        if (buildings.stream().anyMatch(link -> !buildingType.equals(link.mplType()))) {
            throw new IllegalArgumentException("building query links must share the declared type");
        }
    }

    @Override
    public LinkedBuildingSetType type() {
        return new LinkedBuildingSetType(buildingType);
    }
}
