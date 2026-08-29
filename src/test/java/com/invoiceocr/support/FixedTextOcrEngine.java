package com.invoiceocr.support;

import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.ocr.OcrEngine;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double that returns a canned recognition result.
 *
 * <p>Its existence is the point of the {@code OcrEngine} interface: the whole
 * pipeline is testable with no native library and no image files.</p>
 */
public final class FixedTextOcrEngine implements OcrEngine {

    private final String text;
    private final AtomicInteger closeCount = new AtomicInteger();

    public FixedTextOcrEngine(String text) {
        this.text = text;
    }

    @Override
    public RecognizedText recognize(SourceImage image) {
        return RecognizedText.of(text);
    }

    @Override
    public void close() {
        closeCount.incrementAndGet();
    }

    public int closeCount() {
        return closeCount.get();
    }
}
