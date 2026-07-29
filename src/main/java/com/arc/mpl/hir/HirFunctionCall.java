package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunctionCall(String function, List<HirExpression> arguments, MplType type) implements HirExpression {
    public HirFunctionCall {
        Objects.requireNonNull(function, "function");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(type, "type");
    }
}
