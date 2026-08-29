package com.invoiceocr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.support.InMemoryConfigurationSource;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Configuration sources")
class ChainedConfigurationSourceTest {

    @Test
    @DisplayName("the first source that answers wins")
    void respectsPrecedence() {
        ConfigurationSource chain = ChainedConfigurationSource.of(
                new InMemoryConfigurationSource().with("ocr.language", "ron"),
                new InMemoryConfigurationSource().with("ocr.language", "eng").with("ui.locale", "en"));

        assertEquals(Optional.of("ron"), chain.find("ocr.language"));
        assertEquals(Optional.of("en"), chain.find("ui.locale"));
        assertTrue(chain.find("missing.key").isEmpty());
    }

    @Test
    @DisplayName("a blank value is treated as absent, so an empty override cannot mask a real setting")
    void skipsBlankValues() {
        ConfigurationSource chain = ChainedConfigurationSource.of(
                new InMemoryConfigurationSource().with("ocr.language", "   "),
                new InMemoryConfigurationSource().with("ocr.language", "ron"));

        assertEquals(Optional.of("ron"), chain.find("ocr.language"));
    }

    @Test
    @DisplayName("environment lookups translate dotted keys to SCREAMING_SNAKE_CASE")
    void translatesEnvironmentKeys() {
        ConfigurationSource environment = new EnvironmentConfigurationSource(
                Map.of("OCR_TESSDATA_PATH", "/opt/tessdata"),
                EnvironmentConfigurationSource::toEnvironmentName);

        assertEquals(Optional.of("/opt/tessdata"), environment.find(SettingKeys.TESSDATA_PATH));
    }
}
