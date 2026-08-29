package com.invoiceocr.ocr;

import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;

/**
 * Recognises text in an image.
 *
 * <p>This is the seam that keeps Tesseract out of the rest of the application:
 * nothing above this interface imports a native binding, so swapping engines
 * (cloud OCR, a stub in tests) touches one factory and nothing else.</p>
 */
public interface OcrEngine extends AutoCloseable {

    /**
     * @throws com.invoiceocr.exception.OcrExecutionException if recognition fails
     */
    RecognizedText recognize(SourceImage image);

    /** Human-readable engine name, used in logs and diagnostics. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** Releases native resources. Overridden only by engines that hold any. */
    @Override
    default void close() {
        // no-op by default
    }
}
