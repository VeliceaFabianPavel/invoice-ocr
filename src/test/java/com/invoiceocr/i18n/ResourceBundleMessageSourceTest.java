package com.invoiceocr.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResourceBundleMessageSource")
class ResourceBundleMessageSourceTest {

    private final Locale originalDefault = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefault);
    }

    @Test
    @DisplayName("serves the requested language regardless of the host locale")
    void ignoresTheHostLocale() {
        Locale.setDefault(Locale.forLanguageTag("en"));

        MessageSource romanian = ResourceBundleMessageSource.forBaseName("messages", Locale.forLanguageTag("ro"));

        assertEquals("Furnizor", romanian.get("field.supplier"));
    }

    @Test
    @DisplayName("falls back to the base bundle for an unknown language")
    void fallsBackToTheBaseBundle() {
        Locale.setDefault(Locale.forLanguageTag("ro"));

        MessageSource unknown = ResourceBundleMessageSource.forBaseName("messages", Locale.forLanguageTag("fr"));

        assertEquals("Supplier", unknown.get("field.supplier"));
    }

    @Test
    @DisplayName("substitutes arguments into a parameterised message")
    void formatsArguments() {
        MessageSource messages = ResourceBundleMessageSource.forBaseName("messages", Locale.forLanguageTag("en"));

        assertEquals("Recognised fields: 4 of 6", messages.get(MessageKeys.REPORT_FOOTER, 4, 6));
    }

    @Test
    @DisplayName("degrades to the key itself instead of throwing on a missing translation")
    void survivesMissingKeys() {
        MessageSource messages = ResourceBundleMessageSource.forBaseName("messages", Locale.forLanguageTag("en"));

        assertEquals("no.such.key", messages.get("no.such.key"));
        assertNotEquals("", messages.get("no.such.key"));
    }
}
