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
    List<HirStatement> body
) implements HirStatement {
    public HirUnitIteration {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(unitType, "unitType");
        Objects.requireNonNull(mlogType, "mlogType");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }
}
