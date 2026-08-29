package com.invoiceocr.support;

import com.invoiceocr.i18n.MessageSource;
import java.text.MessageFormat;

/**
 * Echoes the key instead of a translation, so formatter assertions describe
 * structure rather than wording.
 */
public final class TestMessageSource implements MessageSource {

    @Override
    public String get(String key, Object... arguments) {
        return arguments.length == 0 ? key : key + MessageFormat.format("({0})", arguments[0]);
    }
}
