package com.arc.mpl.diagnostic;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** A stable, user-facing compiler diagnostic. */
public record Diagnostic(
    Severity severity,
    String code,
    String message,
    Optional<Path> file,
    Optional<SourceSpan> span
) {
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        file = file == null ? Optional.empty() : file;
        span = span == null ? Optional.empty() : span;
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.empty(), Optional.empty());
    }

    public record SourceSpan(int startLine, int startColumn, int endLine, int endColumn) {
        public SourceSpan {
            if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1) {
                throw new IllegalArgumentException("invalid source span");
            }
        }
    }
}
