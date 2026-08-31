package com.invoiceocr.ocr;

/**
 * Creates a ready-to-use {@link OcrEngine}.
 *
 * <p>Engines are created per recognition run rather than shared: native OCR
 * handles are not thread-safe, and validating the installation at creation time
 * turns a misconfiguration into one clear error instead of a native crash.</p>
 *
 * <p>The options argument is what lets one document be read several times with
 * different assumptions about its layout. A caller that has no opinion asks for
 * {@link OcrOptions#inherited()} - or simply calls {@link #create()} - and gets
 * the configured engine unchanged.</p>
 */
@FunctionalInterface
public interface OcrEngineFactory {

    /**
     * @throws com.invoiceocr.exception.ConfigurationException if the engine cannot be configured
     */
    OcrEngine create(OcrOptions options);

    /** The engine as configured, with nothing overridden. */
    default OcrEngine create() {
        return create(OcrOptions.inherited());
    }
}
