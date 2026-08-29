package com.invoiceocr.config;

import java.util.Optional;

/** Reads JVM system properties, so any key can be overridden with -Dkey=value. */
public final class SystemPropertiesConfigurationSource implements ConfigurationSource {

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(System.getProperty(key));
    }
}
