package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirFunction(String name, List<HirFunctionParameter> parameters, MplType returnType,
                          List<HirStatement> body) {
    public HirFunction {
        Objects.requireNonNull(name, "name");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }
}
