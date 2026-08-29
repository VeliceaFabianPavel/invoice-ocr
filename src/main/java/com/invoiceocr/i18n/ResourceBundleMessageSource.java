package com.invoiceocr.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * {@link MessageSource} backed by a {@link ResourceBundle}.
 *
 * <p>A missing key degrades to the key itself instead of throwing: a translation
 * gap must never take the application down.</p>
 */
public final class ResourceBundleMessageSource implements MessageSource {

    private final ResourceBundle bundle;

    public ResourceBundleMessageSource(ResourceBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    /**
     * Loads a bundle for {@code locale}, falling back to the base bundle only.
     *
     * <p>The no-fallback control matters: by default a missing bundle is looked
     * up under the <em>host's</em> locale before the base one, so asking for
     * Romanian on an English machine can silently yield a third language.</p>
     */
    public static MessageSource forBaseName(String baseName, Locale locale) {
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(locale, "locale");
        ResourceBundle.Control control =
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        return new ResourceBundleMessageSource(ResourceBundle.getBundle(baseName, locale, control));
    }

    @Override
    public String get(String key, Object... arguments) {
        String pattern = lookup(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, arguments);
    }

    private String lookup(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
