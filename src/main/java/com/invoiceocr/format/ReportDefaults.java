package com.invoiceocr.format;

import com.invoiceocr.config.ReportSettings;

/**
 * What a report shows when nobody has said otherwise.
 *
 * <p>Formatters take their settings by constructor like everything else, but a
 * formatter is also the thing most often built by hand - in a test, in a script,
 * in a variant assembler - and threading a settings object through those call
 * sites buys nothing. This is the object they get instead.</p>
 */
public final class ReportDefaults {

    private static final ReportSettings ALL = new ReportSettings() {
        @Override
        public boolean showConfidence() {
            return true;
        }

        @Override
        public boolean includeLineItems() {
            return true;
        }
    };

    /** Everything the report can show, shown. */
    public static ReportSettings all() {
        return ALL;
    }

    private ReportDefaults() {
        throw new AssertionError("No instances");
    }
}
