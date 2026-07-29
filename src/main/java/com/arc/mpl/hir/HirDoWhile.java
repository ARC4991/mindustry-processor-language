package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Target-neutral post-tested loop. */
public record HirDoWhile(List<HirStatement> body, HirExpression condition) implements HirStatement {
    public HirDoWhile {
        body = List.copyOf(Objects.requireNonNull(body, "body"));
        Objects.requireNonNull(condition, "condition");
    }
}
