package com.arc.mpl.diagnostic;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Renders keyed compiler text through a language catalogue with a safe fallback. */
public final class DiagnosticMessages {
    private static final String BUNDLE_NAME = "com.arc.mpl.diagnostic.messages";

    private DiagnosticMessages() {
    }

    public static String format(DiagnosticLanguage language, String key, List<?> arguments, String fallback) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, language.locale());
            if (bundle.containsKey(key)) {
                return new MessageFormat(bundle.getString(key), language.locale()).format(arguments.toArray());
            }
        } catch (MissingResourceException ignored) {
            // The source-language fallback remains useful during catalogue rollout.
        }
        return fallback;
    }

    public static String formatChinese(String key, List<?> arguments, String fallback) {
        return format(DiagnosticLanguage.ZH_CN, key, arguments, fallback);
    }

    public static String localeTag(DiagnosticLanguage language) {
        Locale locale = language.locale();
        return locale.toLanguageTag();
    }
}
