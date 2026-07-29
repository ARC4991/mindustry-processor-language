package com.arc.mpl.hir;

import java.util.Objects;

/** Runtime-indexed Array read awaiting physical-Memory lowering. */
public record HirDynamicIndexAccess(HirExpression target, HirExpression index, MplType type) implements HirExpression {
    public HirDynamicIndexAccess {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(type, "type");
    }
}
