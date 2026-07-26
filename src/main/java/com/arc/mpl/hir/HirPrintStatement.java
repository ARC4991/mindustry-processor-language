package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

public record HirPrintStatement(String linkName, List<HirExpression> arguments) implements HirStatement {
    public HirPrintStatement {
        Objects.requireNonNull(linkName, "linkName");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }
}
