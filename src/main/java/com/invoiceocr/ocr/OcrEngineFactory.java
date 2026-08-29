package com.invoiceocr.ocr;

/**
 * Creates a ready-to-use {@link OcrEngine}.
 *
 * <p>Engines are created per recognition run rather than shared: native OCR
 * handles are not thread-safe, and validating the installation at creation time
 * turns a misconfiguration into one clear error instead of a native crash.</p>
 */
@FunctionalInterface
public interface OcrEngineFactory {

    /**
     * @throws com.invoiceocr.exception.ConfigurationException if the engine cannot be configured
     */
    OcrEngine create();
}
