package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunction(String name, String sourceName, List<HirFunctionParameter> parameters, MplType returnType,
                          List<HirStatement> body) {
    public HirFunction {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceName, "sourceName");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }

    public HirFunction(String name, List<HirFunctionParameter> parameters, MplType returnType,
                       List<HirStatement> body) {
        this(name, name, parameters, returnType, body);
    }
}
