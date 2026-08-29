package com.invoiceocr.config;

import java.util.Locale;
import java.util.Objects;

/** Resolves the {@link Locale} used for the user interface. */
@FunctionalInterface
public interface LocaleProvider {

    Locale locale();

    /** Reads {@code ui.locale} from configuration, falling back to the JVM default. */
    static LocaleProvider fromConfiguration(ConfigurationSource source) {
        Objects.requireNonNull(source, "source");
        return () -> source.find(SettingKeys.LOCALE)
                .map(Locale::forLanguageTag)
                .orElseGet(Locale::getDefault);
    }

    static LocaleProvider fixed(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return () -> locale;
    }
}
