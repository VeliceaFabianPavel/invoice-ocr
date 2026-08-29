package com.invoiceocr.i18n;

/**
 * Resolves user-visible text by key.
 *
 * <p>No class outside this package hard-codes a sentence, so the UI language is
 * a resource-bundle concern rather than a code concern.</p>
 */
@FunctionalInterface
public interface MessageSource {

    /**
     * @param key       message key, see {@link MessageKeys}
     * @param arguments optional {@link java.text.MessageFormat} arguments
     * @return the formatted message, never {@code null}
     */
    String get(String key, Object... arguments);
}
