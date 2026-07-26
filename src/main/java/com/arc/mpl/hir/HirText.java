package com.arc.mpl.hir;

import java.util.Objects;

public record HirText(String value) implements HirExpression {
    public HirText { Objects.requireNonNull(value, "value"); }
    @Override public ValueType type() { return ValueType.ERROR; }
}
