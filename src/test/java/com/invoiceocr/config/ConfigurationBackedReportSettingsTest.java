package com.invoiceocr.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.support.InMemoryConfigurationSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfigurationBackedReportSettings")
class ConfigurationBackedReportSettingsTest {

    @Test
    @DisplayName("both additions are on unless they are turned off")
    void defaultsToOn() {
        ReportSettings settings =
                new ConfigurationBackedReportSettings(new InMemoryConfigurationSource());

        assertTrue(settings.showConfidence());
        assertTrue(settings.includeLineItems());
    }

    @Test
    @DisplayName("either can be turned off on its own")
    void readsEachFlagSeparately() {
        ReportSettings settings = new ConfigurationBackedReportSettings(
                new InMemoryConfigurationSource().with(SettingKeys.SHOW_CONFIDENCE, "false"));

        assertFalse(settings.showConfidence());
        assertTrue(settings.includeLineItems());
    }
}
