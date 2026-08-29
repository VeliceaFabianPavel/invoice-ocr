package com.invoiceocr.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Asks each delegate in order and returns the first non-blank value. */
public final class ChainedConfigurationSource implements ConfigurationSource {

    private final List<ConfigurationSource> delegates;

    public ChainedConfigurationSource(List<ConfigurationSource> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
    }

    public static ChainedConfigurationSource of(ConfigurationSource... delegates) {
        return new ChainedConfigurationSource(List.of(delegates));
    }

    @Override
    public Optional<String> find(String key) {
        for (ConfigurationSource delegate : delegates) {
            Optional<String> value = delegate.find(key).map(String::trim).filter(v -> !v.isEmpty());
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
