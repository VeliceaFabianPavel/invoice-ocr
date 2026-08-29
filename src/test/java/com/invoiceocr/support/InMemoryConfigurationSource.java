package com.invoiceocr.support;

import com.invoiceocr.config.ConfigurationSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Mutable in-memory settings, so configuration tests need no files. */
public final class InMemoryConfigurationSource implements ConfigurationSource {

    private final Map<String, String> values = new HashMap<>();

    public InMemoryConfigurationSource with(String key, String value) {
        values.put(key, value);
        return this;
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
