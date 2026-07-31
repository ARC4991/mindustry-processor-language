package com.arc.mpl.hir;

import java.util.Objects;

public record HirFunctionParameter(String name, MplType type, int aggregateSize) {
    public HirFunctionParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (aggregateSize < 0) throw new IllegalArgumentException("aggregateSize 不能为负数");
        if (!isStructured(type) && aggregateSize != 0) {
            throw new IllegalArgumentException("只有元组或数组参数才能携带 aggregateSize");
        }
    }

    public HirFunctionParameter(String name, MplType type) {
        this(name, type, 0);
    }

    private static boolean isStructured(MplType type) {
        return type instanceof TupleType || type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.ARRAY;
    }
}
