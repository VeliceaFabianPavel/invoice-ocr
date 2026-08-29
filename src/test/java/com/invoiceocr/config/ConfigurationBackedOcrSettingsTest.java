package com.invoiceocr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static OcrSettings settingsWith(ConfigurationSource source) {
        return new ConfigurationBackedOcrSettings(source);
    }
}
