package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Target-neutral conditional branch. */
public record HirIf(HirExpression condition, List<HirStatement> thenBody, Optional<List<HirStatement>> elseBody)
    implements HirStatement {
    public HirIf {
        Objects.requireNonNull(condition, "condition");
        thenBody = List.copyOf(Objects.requireNonNull(thenBody, "thenBody"));
        elseBody = Objects.requireNonNull(elseBody, "elseBody").map(List::copyOf);
    }
}
