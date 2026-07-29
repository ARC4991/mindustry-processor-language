package com.arc.mpl.mil.syntax;

import com.arc.mpl.diagnostic.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A MIL document is available only after syntax and profile macro checks succeed. */
public record MilParseResult(Optional<MilDocument> document, List<Diagnostic> diagnostics) {
    public MilParseResult {
        document = document == null ? Optional.empty() : document;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean succeeded() {
        return document.isPresent() && diagnostics.isEmpty();
    }
}
