package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A control command directed at the Unit currently bound by a HirUnitIteration. */
public record HirUnitControl(String bindingName, String command, List<HirExpression> arguments) implements HirStatement {
    public HirUnitControl {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(command, "command");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }
}
