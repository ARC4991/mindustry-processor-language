package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunction(String name, String sourceName, List<HirFunctionParameter> parameters, MplType returnType,
                          int aggregateReturnSize, List<HirStatement> body) {
    public HirFunction {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceName, "sourceName");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        if (aggregateReturnSize < 0) throw new IllegalArgumentException("aggregateReturnSize 不能为负数");
        if (!isStructured(returnType) && aggregateReturnSize != 0) {
            throw new IllegalArgumentException("只有元组或数组返回值才能携带 aggregateReturnSize");
        }
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }

    public HirFunction(String name, String sourceName, List<HirFunctionParameter> parameters, MplType returnType,
                       List<HirStatement> body) {
        this(name, sourceName, parameters, returnType, 0, body);
    }

    public HirFunction(String name, List<HirFunctionParameter> parameters, MplType returnType,
                       List<HirStatement> body) {
        this(name, name, parameters, returnType, 0, body);
    }

    private static boolean isStructured(MplType type) {
        return type instanceof TupleType || type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.ARRAY;
    }
}
