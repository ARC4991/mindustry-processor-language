package com.arc.mpl.hir;

import java.util.Objects;

/** A strongly typed read-only member access, such as {@code unit.health}. */
public record HirMemberAccess(HirExpression target, String member, ValueType type) implements HirExpression {
    public HirMemberAccess {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(type, "type");
    }
}
