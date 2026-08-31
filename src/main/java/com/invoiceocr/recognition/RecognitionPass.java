package com.invoiceocr.recognition;

import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.ocr.OcrOptions;
import java.util.Objects;

/**
 * One way of looking at a page: a preparation of the image, and an assumption
 * about how the text on it is laid out.
 *
 * <p>Recognition has always been a gamble on two guesses made before the page
 * was ever seen — that this amount of preprocessing suits it, and that this
 * segmentation mode describes it. Neither is knowable in advance, and getting
 * either wrong costs whole fields. A pass makes the guess explicit and therefore
 * repeatable with a different one.</p>
 *
 * @param name         short identifier, carried into logs and diagnostics
 * @param preprocessor how the image is prepared for this attempt
 * @param options      what the engine is told about the layout
 */
public record RecognitionPass(String name, ImagePreprocessor preprocessor, OcrOptions options) {

    public RecognitionPass {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(preprocessor, "preprocessor");
        Objects.requireNonNull(options, "options");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A pass must have a name");
        }
    }

    public static RecognitionPass of(String name, ImagePreprocessor preprocessor) {
        return new RecognitionPass(name, preprocessor, OcrOptions.inherited());
    }

    public static RecognitionPass of(String name, ImagePreprocessor preprocessor, int pageSegmentationMode) {
        return new RecognitionPass(name, preprocessor, OcrOptions.pageSegmentation(pageSegmentationMode));
    }

    @Override
    public String toString() {
        return name;
    }
}
