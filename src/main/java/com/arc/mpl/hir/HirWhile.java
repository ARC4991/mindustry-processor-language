package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Target-neutral structured while loop. */
public record HirWhile(HirExpression condition, List<HirStatement> body) implements HirStatement {
    public HirWhile {
        Objects.requireNonNull(condition, "condition");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }
}
