package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** One target-independent command appended to the active Display draw buffer. */
public record HirDraw(String displayName, Command command, List<HirExpression> arguments) implements HirStatement {
    public HirDraw {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(command, "command");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (arguments.size() != command.argumentCount()) {
            throw new IllegalArgumentException("draw command argument count mismatch: " + command);
        }
    }

    public enum Command {
        CLEAR(3), COLOR(4), RECT(4), LINE_RECT(4), LINE(4);

        private final int argumentCount;

        Command(int argumentCount) { this.argumentCount = argumentCount; }

        public int argumentCount() { return argumentCount; }
    }
}
