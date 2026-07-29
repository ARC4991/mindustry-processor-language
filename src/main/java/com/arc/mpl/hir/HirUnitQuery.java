package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A lazy UnitSet descriptor; evaluating the descriptor itself emits no target instruction. */
public record HirUnitQuery(
    String bindingName,
    String unitType,
    String mlogType,
    List<HirExpression> filters,
    int managedLimit,
    int managedId
) implements HirExpression {
    public HirUnitQuery {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(unitType, "unitType");
        Objects.requireNonNull(mlogType, "mlogType");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        if (managedLimit < 0) throw new IllegalArgumentException("managedLimit must not be negative");
        if (managedLimit == 0 && managedId != -1 || managedLimit > 0 && managedId < 0) {
            throw new IllegalArgumentException("managedId must identify exactly the managed queries");
        }
    }

    public HirUnitQuery(String bindingName, String unitType, String mlogType, List<HirExpression> filters,
                        int managedLimit) {
        this(bindingName, unitType, mlogType, filters, managedLimit, managedLimit > 0 ? 0 : -1);
    }

    @Override
    public UnitSetType type() {
        return new UnitSetType(unitType);
    }

    public boolean hasManagedLimit() {
        return managedLimit > 0;
    }
}
