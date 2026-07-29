package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A statically bounded object allocation followed by its constructor call. */
public record HirNewObject(int allocationId, String className, String constructorFunction,
                           List<HirExpression> arguments, ObjectType type) implements HirExpression {
    public HirNewObject {
        if (allocationId < 1) throw new IllegalArgumentException("allocationId 必须为正数");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(constructorFunction, "constructorFunction");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(type, "type");
        if (!className.equals(type.className()) || type.nullable()) {
            throw new IllegalArgumentException("new 必须产生同名非空对象类型");
        }
    }
}
