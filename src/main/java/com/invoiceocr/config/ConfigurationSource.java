package com.invoiceocr.config;

import java.util.List;
import java.util.Optional;

/**
 * A read-only source of string settings addressed by dotted keys
 * (for example {@code ocr.tessdata.path}).
 *
 * <p>Deliberately tiny: every concrete source (file, environment, system
 * properties, in-memory map for tests) implements exactly one method, and they
 * compose through {@link ChainedConfigurationSource}.</p>
 */
@FunctionalInterface
public interface ConfigurationSource {

    Optional<String> find(String key);

    /** Returns a source that consults {@code this} first and {@code fallback} second. */
    default ConfigurationSource orElse(ConfigurationSource fallback) {
        return new ChainedConfigurationSource(List.of(this, fallback));
    }

    /** A source that knows nothing; useful as a neutral element. */
    static ConfigurationSource empty() {
        return key -> Optional.empty();
    }
}
