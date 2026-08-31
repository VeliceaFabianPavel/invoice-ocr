package com.invoiceocr.config;

/** Single place where configuration key names are spelled out. */
public final class SettingKeys {

    public static final String TESSDATA_PATH = "ocr.tessdata.path";
    public static final String LANGUAGE = "ocr.language";
    public static final String PAGE_SEGMENTATION_MODE = "ocr.pageSegmentationMode";
    public static final String ENGINE_MODE = "ocr.engineMode";
    public static final String SUPPORTED_EXTENSIONS = "document.supportedExtensions";
    public static final String PREPROCESSING_ENABLED = "image.preprocessing.enabled";
    public static final String MINIMUM_WIDTH = "image.preprocessing.minimumWidth";
    public static final String LOCALE = "ui.locale";
    public static final String EXPORT_DEFAULT_FORMAT = "export.defaultFormat";

    // --- 1.2.0 ---

    /** How many times a page may be read before the results are merged. */
    public static final String MAXIMUM_PASSES = "ocr.passes.maximum";

    /** Mean confidence at which a reading is good enough to stop early. */
    public static final String TARGET_CONFIDENCE = "ocr.passes.targetConfidence";

    /** Whether the goods table is read as well as the header fields. */
    public static final String LINE_ITEMS_ENABLED = "extraction.lineItems.enabled";

    /** Whether reports mark the values that were found by a strategy that guesses. */
    public static final String SHOW_CONFIDENCE = "report.showConfidence";

    /** Whether reports carry the rows of the goods table. */
    public static final String REPORT_LINE_ITEMS = "report.lineItems";

    private SettingKeys() {
        throw new AssertionError("No instances");
    }
}
