package com.invoiceocr.ocr;

/**
 * Per-run overrides for an OCR engine.
 *
 * <p>Everything an engine needs normally comes from configuration, and stays
 * fixed for the life of the application. One thing cannot: the page
 * segmentation mode is a statement about the <em>layout</em> of a particular
 * page, and the whole point of reading a page more than once is to try more than
 * one statement about it.</p>
 *
 * <p>An option left inherited means "whatever the configuration says", so a
 * single-pass run behaves exactly as it did before this type existed.</p>
 *
 * @param pageSegmentationMode Tesseract PSM, or {@link #INHERIT}
 */
public record OcrOptions(int pageSegmentationMode) {

    /** Sentinel for "do not override; use the configured value". */
    public static final int INHERIT = -1;

    private static final OcrOptions INHERITED = new OcrOptions(INHERIT);

    public static OcrOptions inherited() {
        return INHERITED;
    }

    public static OcrOptions pageSegmentation(int mode) {
        if (mode < 0) {
            throw new IllegalArgumentException("Page segmentation mode must not be negative: " + mode);
        }
        return new OcrOptions(mode);
    }

    public boolean overridesPageSegmentation() {
        return pageSegmentationMode != INHERIT;
    }

    /** The mode to use, given what configuration would otherwise have supplied. */
    public int pageSegmentationModeOr(int configured) {
        return overridesPageSegmentation() ? pageSegmentationMode : configured;
    }
}
