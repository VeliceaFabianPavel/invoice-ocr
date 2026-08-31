package com.invoiceocr.config;

import java.util.Objects;

/** Adapts an untyped {@link ConfigurationSource} to the {@link ReportSettings} contract. */
public final class ConfigurationBackedReportSettings implements ReportSettings {

    private final ConfigurationSource source;

    public ConfigurationBackedReportSettings(ConfigurationSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public boolean showConfidence() {
        return flag(SettingKeys.SHOW_CONFIDENCE);
    }

    @Override
    public boolean includeLineItems() {
        return flag(SettingKeys.REPORT_LINE_ITEMS);
    }

    /** Both flags default to on: a new capability that has to be discovered is not one. */
    private boolean flag(String key) {
        return source.find(key).map(Boolean::parseBoolean).orElse(Boolean.TRUE);
    }
}
