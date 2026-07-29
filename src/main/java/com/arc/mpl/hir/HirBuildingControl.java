package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** Strongly typed control action on a building declared in {@code hardware.mplh}. */
public record HirBuildingControl(HirHardwareLink target, String action, List<HirExpression> arguments) implements HirStatement {
    public HirBuildingControl {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
    }
}
