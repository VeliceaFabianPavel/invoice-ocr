package com.invoiceocr.config;

import com.invoiceocr.exception.ConfigurationException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Adapts an untyped {@link ConfigurationSource} to the typed {@link OcrSettings} contract. */
public final class ConfigurationBackedOcrSettings implements OcrSettings {

    private static final String DEFAULT_LANGUAGE = "eng";
    private static final int DEFAULT_PAGE_SEGMENTATION_MODE = 3;   // fully automatic
    private static final int DEFAULT_ENGINE_MODE = 3;              // whatever the install provides
    private static final int DEFAULT_MINIMUM_WIDTH = 1000;
    private static final int DEFAULT_MAXIMUM_PASSES = 4;
    private static final int MAXIMUM_PASSES_ALLOWED = 4;
    private static final double DEFAULT_TARGET_CONFIDENCE = 0.80;
    private static final List<String> DEFAULT_EXTENSIONS =
            List.of("png", "jpg", "jpeg", "bmp", "tif", "tiff", "gif");

    private final ConfigurationSource source;

    public ConfigurationBackedOcrSettings(ConfigurationSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Path tessdataPath() {
        return source.find(SettingKeys.TESSDATA_PATH)
                .map(Path::of)
                .orElseThrow(() -> new ConfigurationException(missingTessdataMessage()));
    }

    private static String missingTessdataMessage() {
        return "Missing setting " + SettingKeys.TESSDATA_PATH
                + ". Set it in application.properties, pass -D" + SettingKeys.TESSDATA_PATH
                + "=... to the JVM, or export "
                + EnvironmentConfigurationSource.toEnvironmentName(SettingKeys.TESSDATA_PATH) + ".";
    }

    @Override
    public String language() {
        return source.find(SettingKeys.LANGUAGE).orElse(DEFAULT_LANGUAGE);
    }

    @Override
    public int pageSegmentationMode() {
        return integer(SettingKeys.PAGE_SEGMENTATION_MODE, DEFAULT_PAGE_SEGMENTATION_MODE);
    }

    @Override
    public int engineMode() {
        return integer(SettingKeys.ENGINE_MODE, DEFAULT_ENGINE_MODE);
    }

    @Override
    public List<String> supportedExtensions() {
        return source.find(SettingKeys.SUPPORTED_EXTENSIONS)
                .map(ConfigurationBackedOcrSettings::splitExtensions)
                .filter(list -> !list.isEmpty())
                .orElse(DEFAULT_EXTENSIONS);
    }

    private static List<String> splitExtensions(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .toList();
    }

    @Override
    public boolean preprocessingEnabled() {
        return source.find(SettingKeys.PREPROCESSING_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.TRUE);
    }

    @Override
    public int minimumWidth() {
        return integer(SettingKeys.MINIMUM_WIDTH, DEFAULT_MINIMUM_WIDTH);
    }

    @Override
    public int maximumPasses() {
        int configured = integer(SettingKeys.MAXIMUM_PASSES, DEFAULT_MAXIMUM_PASSES);
        return Math.max(1, Math.min(MAXIMUM_PASSES_ALLOWED, configured));
    }

    /**
     * Out-of-range values are clamped rather than rejected. A target of 2.0 means
     * "always run every pass" and a target of 0 means "accept the first" — both
     * are reasonable intentions badly expressed, and neither is worth refusing to
     * start over.
     */
    @Override
    public double targetConfidence() {
        Optional<String> raw = source.find(SettingKeys.TARGET_CONFIDENCE);
        if (raw.isEmpty()) {
            return DEFAULT_TARGET_CONFIDENCE;
        }
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(raw.get().trim())));
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Setting " + SettingKeys.TARGET_CONFIDENCE
                    + " must be a number between 0 and 1 but was " + raw.get(), e);
        }
    }

    @Override
    public boolean lineItemsEnabled() {
        return source.find(SettingKeys.LINE_ITEMS_ENABLED)
                .map(Boolean::parseBoolean)
                .orElse(Boolean.TRUE);
    }

    private int integer(String key, int fallback) {
        Optional<String> raw = source.find(key);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.get().trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Setting " + key + " must be an integer but was " + raw.get(), e);
        }
    }
}
