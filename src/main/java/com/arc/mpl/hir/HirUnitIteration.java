package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/**
 * A profile-resolved UnitSet traversal. {@code mlogType} is the target content
 * constant without its leading {@code @}, for example {@code dagger}.
 */
public record HirUnitIteration(
    String bindingName,
    String unitType,
    String mlogType,
    List<HirExpression> filters,
    int managedLimit,
    int managedId,
    List<HirStatement> body
) implements HirStatement {
    public HirUnitIteration {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(unitType, "unitType");
        Objects.requireNonNull(mlogType, "mlogType");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        if (managedLimit < 0) {
            throw new IllegalArgumentException("managedLimit must not be negative");
        }
        if (managedLimit == 0 && managedId != -1 || managedLimit > 0 && managedId < 0) {
            throw new IllegalArgumentException("managedId must identify exactly the managed traversals");
        }
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }

    public HirUnitIteration(String bindingName, String unitType, String mlogType, List<HirExpression> filters,
                            int managedLimit, List<HirStatement> body) {
        this(bindingName, unitType, mlogType, filters, managedLimit, managedLimit > 0 ? 0 : -1, body);
    }

    /**
     * Creates an ordinary weakly-consistent traversal with no managed subset.
     *
     * <p>A positive {@code managedLimit} is emitted only for the explicit
     * {@code UnitSet.take(n)} form. It makes the compiler privately retain at
     * most {@code n} units for this traversal; the public MPL program never
     * observes the runtime ownership data.</p>
     */
    public HirUnitIteration(
        String bindingName,
        String unitType,
        String mlogType,
        List<HirExpression> filters,
        List<HirStatement> body
    ) {
        this(bindingName, unitType, mlogType, filters, 0, -1, body);
    }

    public boolean hasManagedLimit() {
        return managedLimit > 0;
    }
}
