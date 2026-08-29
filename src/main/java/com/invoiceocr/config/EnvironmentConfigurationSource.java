package com.invoiceocr.config;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Reads environment variables, translating dotted keys to the usual shell
 * convention: {@code ocr.tessdata.path} becomes {@code OCR_TESSDATA_PATH}.
 */
public final class EnvironmentConfigurationSource implements ConfigurationSource {

    private final Map<String, String> environment;
    private final UnaryOperator<String> keyMapper;

    public EnvironmentConfigurationSource() {
        this(System.getenv(), EnvironmentConfigurationSource::toEnvironmentName);
    }

    public EnvironmentConfigurationSource(Map<String, String> environment, UnaryOperator<String> keyMapper) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.keyMapper = Objects.requireNonNull(keyMapper, "keyMapper");
    }

    public static String toEnvironmentName(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(environment.get(keyMapper.apply(key)));
    }
}
