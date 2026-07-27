package com.arc.mpl.diagnostic;

import java.util.Locale;
import java.util.Optional;

/**
 * Language selected when compiler-owned information and diagnostics are rendered.
 *
 * <p>Chinese is the first shipped catalogue and remains the default. New
 * catalogues can be added without changing the compiler's stable diagnostic
 * codes or call sites.</p>
 */
public enum DiagnosticLanguage {
    ZH_CN("zh-CN", Locale.SIMPLIFIED_CHINESE);

    private final String id;
    private final Locale locale;

    DiagnosticLanguage(String id, Locale locale) {
        this.id = id;
        this.locale = locale;
    }

    public String id() {
        return id;
    }

    public Locale locale() {
        return locale;
    }

    public static Optional<DiagnosticLanguage> parse(String value) {
        if (value == null) return Optional.empty();
        for (DiagnosticLanguage language : values()) {
            if (language.id.equalsIgnoreCase(value)) return Optional.of(language);
        }
        return Optional.empty();
    }
}
