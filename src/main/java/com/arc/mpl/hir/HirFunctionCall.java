package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunctionCall(String function, List<HirExpression> arguments, MplType type,
                              int stringResultAllocationId) implements HirExpression {
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
    }

    public HirFunctionCall(String function, List<HirExpression> arguments, MplType type) {
        this(function, arguments, type, 0);
    }
}
