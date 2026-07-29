package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Statically traverses the declared hardware links of one target building type. */
public record HirBuildingIteration(
    String bindingName,
    String buildingType,
    List<HirHardwareLink> buildings,
    List<HirExpression> filters,
    List<HirStatement> body
) implements HirStatement {
    public HirBuildingIteration {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(buildingType, "buildingType");
        buildings = List.copyOf(Objects.requireNonNull(buildings, "buildings"));
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        body = List.copyOf(Objects.requireNonNull(body, "body"));
        if (buildings.stream().anyMatch(link -> !buildingType.equals(link.mplType()))) {
            throw new IllegalArgumentException("building iteration links must share the declared type");
        }
    }
}
