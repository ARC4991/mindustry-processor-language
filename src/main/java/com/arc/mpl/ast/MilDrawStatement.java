package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** Raw public {@code @io.draw} arguments retained until typed hardware analysis. */
public record MilDrawStatement(String hardwareName, String command, List<Expression> arguments, SourceSpan span)
    implements Statement {
    public MilDrawStatement {
        Objects.requireNonNull(hardwareName, "hardwareName");
        Objects.requireNonNull(command, "command");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(span, "span");
    }
}
