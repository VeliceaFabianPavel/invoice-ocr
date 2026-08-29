package com.invoiceocr.config;

import com.invoiceocr.export.ExportFormat;
import com.invoiceocr.export.ExportFormats;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Reads {@code export.defaultFormat} from configuration.
 *
 * <p>An unknown format name falls back to the default rather than failing:
 * a typo in a settings file should not stop the application from starting,
 * and the log says what was ignored.</p>
 */
public final class ConfigurationBackedExportSettings implements ExportSettings {

    private static final Logger LOG = System.getLogger(ConfigurationBackedExportSettings.class.getName());
    private static final ExportFormat FALLBACK = ExportFormats.PDF;

    private final ConfigurationSource source;

    public ConfigurationBackedExportSettings(ConfigurationSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public ExportFormat defaultFormat() {
        return source.find(SettingKeys.EXPORT_DEFAULT_FORMAT)
                .map(configured -> ExportFormats.byId(configured).orElseGet(() -> {
                    LOG.log(Level.WARNING, () -> "Unknown " + SettingKeys.EXPORT_DEFAULT_FORMAT + " '"
                            + configured + "', using " + FALLBACK.id());
                    return FALLBACK;
                }))
                .orElse(FALLBACK);
    }
}
