package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** A named module import with optional future package hardware injection arguments. */
public record ImportDeclaration(
    List<String> names,
    String source,
    List<HardwareArgument> hardwareArguments,
    SourceSpan span
) {
    public ImportDeclaration {
        names = List.copyOf(Objects.requireNonNull(names, "names"));
        if (names.isEmpty()) throw new IllegalArgumentException("import 至少需要一个名称");
        Objects.requireNonNull(source, "source");
        hardwareArguments = List.copyOf(Objects.requireNonNull(hardwareArguments, "hardwareArguments"));
        Objects.requireNonNull(span, "span");
    }

    public record HardwareArgument(String name, String value, SourceSpan span) {
        public HardwareArgument {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(span, "span");
        }
    }
}
