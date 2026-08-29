package com.invoiceocr.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Typed, validated view over the raw configuration.
 *
 * <p>Consumers depend on this interface instead of a {@link ConfigurationSource}
 * so they never parse strings and can be handed a hand-written stub in tests.</p>
 */
public interface OcrSettings {

    /** Directory containing the {@code *.traineddata} files. */
    Path tessdataPath();

    /** Tesseract language code, e.g. {@code eng} or {@code ron+eng}. */
    String language();

    /** Tesseract page segmentation mode (PSM). */
    int pageSegmentationMode();

    /** Tesseract OCR engine mode (OEM). */
    int engineMode();

    /** Lower-case file extensions, without the dot, accepted by the chooser and the loader. */
    List<String> supportedExtensions();

    /** Whether images run through the preprocessing pipeline before OCR. */
    boolean preprocessingEnabled();

    /** Images narrower than this get upscaled before OCR; {@code 0} disables upscaling. */
    int minimumWidth();
}
