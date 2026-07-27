package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A profile-independent standard intrinsic selected by semantic analysis. */
public record HirIntrinsicCall(
    String namespace,
    String name,
    List<HirExpression> arguments,
    ValueType type
) implements HirExpression {
    public HirIntrinsicCall {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(type, "type");
    }
}
