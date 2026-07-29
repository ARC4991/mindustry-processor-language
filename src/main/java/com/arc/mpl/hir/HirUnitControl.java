package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A control command directed at either the current iteration binding or a stored UnitRef. */
public record HirUnitControl(String bindingName, boolean storedReference, String command,
                             List<HirExpression> arguments) implements HirStatement {
    public HirUnitControl {
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(command, "command");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }

    public HirUnitControl(String bindingName, String command, List<HirExpression> arguments) {
        this(bindingName, false, command, arguments);
    }
}
