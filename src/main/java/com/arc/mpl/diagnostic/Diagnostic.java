package com.arc.mpl.diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A stable, user-facing compiler diagnostic. */
public record Diagnostic(
    Severity severity,
    String code,
    String message,
    Optional<Path> file,
    Optional<SourceSpan> span,
    Optional<String> messageKey,
    List<Object> messageArguments
) {
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        file = file == null ? Optional.empty() : file;
        span = span == null ? Optional.empty() : span;
        messageKey = messageKey == null ? Optional.empty() : messageKey;
        messageArguments = List.copyOf(Objects.requireNonNull(messageArguments, "messageArguments"));
    }

    /** Compatibility constructor for existing source diagnostics during catalogue migration. */
    public Diagnostic(
        Severity severity,
        String code,
        String message,
        Optional<Path> file,
        Optional<SourceSpan> span
    ) {
        this(severity, code, message, file, span, Optional.empty(), List.of());
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.empty(), Optional.empty());
    }

    public static Diagnostic localized(
        Severity severity,
        String code,
        String key,
        List<?> arguments,
        Optional<Path> file,
        Optional<SourceSpan> span
    ) {
        List<Object> copiedArguments = arguments.stream().map(value -> (Object) value).toList();
        String chinese = DiagnosticMessages.formatChinese(key, copiedArguments, key);
        return new Diagnostic(severity, code, chinese, file, span, Optional.of(key), copiedArguments);
    }

    public static Diagnostic localizedError(String code, String key, Object... arguments) {
        return localized(Severity.ERROR, code, key, List.of(arguments), Optional.empty(), Optional.empty());
    }

    /** Renders this diagnostic in the requested language; current source diagnostics fall back to Chinese. */
    public String render(DiagnosticLanguage language) {
        return messageKey.map(key -> DiagnosticMessages.format(language, key, messageArguments, message)).orElse(message);
    }

    public record SourceSpan(int startLine, int startColumn, int endLine, int endColumn) {
        public SourceSpan {
            if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1) {
                throw new IllegalArgumentException("invalid source span");
            }
        }
    }
}
