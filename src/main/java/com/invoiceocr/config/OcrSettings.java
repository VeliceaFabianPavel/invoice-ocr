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

    /**
     * How many differently-prepared readings of one page may be made before the
     * results are merged. {@code 1} restores the single-pass behaviour of 1.1.
     */
    int maximumPasses();

    /**
     * Mean confidence at which a reading is accepted without trying another
     * preparation of the image. Higher means more passes and better accuracy on
     * awkward pages; lower means faster.
     */
    double targetConfidence();

    /** Whether the rows of the goods table are read as well as the header fields. */
    boolean lineItemsEnabled();
}
