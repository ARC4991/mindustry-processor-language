package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunctionCall(String function, List<HirExpression> arguments, MplType type,
                              int stringResultAllocationId, int aggregateSize) implements HirExpression {
    public HirFunctionCall {
        Objects.requireNonNull(function, "function");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(type, "type");
        if (type == ValueType.STRING && stringResultAllocationId < 1) {
            throw new IllegalArgumentException("String 函数调用必须拥有独立结果 allocationId");
        }
        if (type != ValueType.STRING && stringResultAllocationId != 0) {
            throw new IllegalArgumentException("只有 String 函数调用才能拥有结果 allocationId");
        }
        if (aggregateSize < 0) throw new IllegalArgumentException("aggregateSize 不能为负数");
        if (!isArray(type) && aggregateSize != 0) {
            throw new IllegalArgumentException("只有数组函数调用才能携带 aggregateSize");
        }
    }

    public HirFunctionCall(String function, List<HirExpression> arguments, MplType type,
                           int stringResultAllocationId) {
        this(function, arguments, type, stringResultAllocationId, 0);
    }

    public HirFunctionCall(String function, List<HirExpression> arguments, MplType type) {
        this(function, arguments, type, 0, 0);
    }

    private static boolean isArray(MplType type) {
        return type instanceof CollectionType collection && collection.kind() == CollectionType.Kind.ARRAY;
    }
}
