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

    private SettingKeys() {
        throw new AssertionError("No instances");
    }
}
