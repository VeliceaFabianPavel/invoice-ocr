package com.invoiceocr.ocr.tesseract;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.exception.OcrExecutionException;
import com.invoiceocr.ocr.OcrEngine;
import java.util.Objects;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * {@link OcrEngine} implemented with Tess4J (a JNA binding over libtesseract).
 *
 * <p>The Tess4J handle is supplied rather than constructed so tests can drive
 * this class with a fake {@link ITesseract} and no native library present.</p>
 */
public final class TesseractOcrEngine implements OcrEngine {

    private final ITesseract tesseract;
    private final OcrSettings settings;

    public TesseractOcrEngine(ITesseract tesseract, OcrSettings settings) {
        this.tesseract = Objects.requireNonNull(tesseract, "tesseract");
        this.settings = Objects.requireNonNull(settings, "settings");
        configure();
    }

    private void configure() {
        tesseract.setDatapath(settings.tessdataPath().toString());
        tesseract.setLanguage(settings.language());
        tesseract.setPageSegMode(settings.pageSegmentationMode());
        tesseract.setOcrEngineMode(settings.engineMode());
    }

    @Override
    public RecognizedText recognize(SourceImage image) {
        Objects.requireNonNull(image, "image");
        try {
            String text = tesseract.doOCR(image.image());
            if (text == null || text.isBlank()) {
                throw new OcrExecutionException(
                        "OCR produced no text for " + image.origin().getFileName());
            }
            return RecognizedText.of(text);
        } catch (TesseractException e) {
            throw new OcrExecutionException(
                    "OCR failed for " + image.origin().getFileName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "Tesseract(" + settings.language() + ")";
    }
}
