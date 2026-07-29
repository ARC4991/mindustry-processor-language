package com.arc.mpl.hir;

import java.util.Objects;
import java.util.Optional;

public record HirReturn(Optional<HirExpression> value) implements HirStatement {
    public HirReturn {
        value = Objects.requireNonNull(value, "value");
    }
}
