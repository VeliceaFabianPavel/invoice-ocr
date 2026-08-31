package com.invoiceocr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.exception.ConfigurationException;
import com.invoiceocr.support.InMemoryConfigurationSource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigurationBackedOcrSettings")
class ConfigurationBackedOcrSettingsTest {

    @Test
    @DisplayName("falls back to defaults for everything but the tessdata path")
    void appliesDefaults() {
        OcrSettings settings = settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.TESSDATA_PATH, "C:/tessdata"));

        assertEquals(Path.of("C:/tessdata"), settings.tessdataPath());
        assertEquals("eng", settings.language());
        assertEquals(3, settings.pageSegmentationMode());
        assertTrue(settings.preprocessingEnabled());
        assertTrue(settings.supportedExtensions().contains("png"));
    }

    @Test
    @DisplayName("explains what to do when the tessdata path is missing")
    void reportsMissingTessdataPath() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> settingsWith(new InMemoryConfigurationSource()).tessdataPath());

        assertTrue(failure.getMessage().contains(SettingKeys.TESSDATA_PATH));
        assertTrue(failure.getMessage().contains("OCR_TESSDATA_PATH"));
    }

    @Test
    @DisplayName("parses the extension list, dots and casing included")
    void parsesExtensions() {
        OcrSettings settings = settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.SUPPORTED_EXTENSIONS, " .PNG, jpg ,, TIFF "));

        assertEquals(List.of("png", "jpg", "tiff"), settings.supportedExtensions());
    }

    @Test
    @DisplayName("rejects a non-numeric integer setting instead of silently defaulting")
    void rejectsMalformedIntegers() {
        OcrSettings settings = settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.PAGE_SEGMENTATION_MODE, "auto"));

        assertThrows(ConfigurationException.class, settings::pageSegmentationMode);
    }
    @Test
    @DisplayName("defaults the 1.2 settings to the full ladder with the table turned on")
    void appliesTheNewDefaults() {
        OcrSettings settings = settingsWith(new InMemoryConfigurationSource());

        assertEquals(4, settings.maximumPasses());
        assertEquals(0.80, settings.targetConfidence(), 1e-9);
        assertTrue(settings.lineItemsEnabled());
    }

    @Test
    @DisplayName("reads the configured number of passes")
    void readsTheConfiguredPassCount() {
        assertEquals(2, settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.MAXIMUM_PASSES, "2")).maximumPasses());
    }

    @Test
    @DisplayName("a pass count outside what the ladder offers is clamped, not refused")
    void clampsThePassCount() {
        assertEquals(1, settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.MAXIMUM_PASSES, "0")).maximumPasses());
        assertEquals(4, settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.MAXIMUM_PASSES, "99")).maximumPasses());
    }

    @Test
    @DisplayName("a target outside the 0..1 scale is brought back onto it")
    void clampsTheTarget() {
        assertEquals(1.0, settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.TARGET_CONFIDENCE, "2.0")).targetConfidence(), 1e-9);
        assertEquals(0.0, settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.TARGET_CONFIDENCE, "-1")).targetConfidence(), 1e-9);
    }

    @Test
    @DisplayName("a target that is not a number is a mistake worth reporting")
    void rejectsAMalformedTarget() {
        OcrSettings settings = settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.TARGET_CONFIDENCE, "high"));

        assertThrows(ConfigurationException.class, settings::targetConfidence);
    }

    @Test
    @DisplayName("the goods table can be turned off")
    void readsTheLineItemFlag() {
        assertFalse(settingsWith(new InMemoryConfigurationSource()
                .with(SettingKeys.LINE_ITEMS_ENABLED, "false")).lineItemsEnabled());
    }

    private static OcrSettings settingsWith(ConfigurationSource source) {
        return new ConfigurationBackedOcrSettings(source);
    }
}
