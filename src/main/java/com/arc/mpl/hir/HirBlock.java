package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A lexical block retained until structured-control-flow lowering. */
public record HirBlock(List<HirStatement> statements) implements HirStatement {
    public HirBlock {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
    }
}
