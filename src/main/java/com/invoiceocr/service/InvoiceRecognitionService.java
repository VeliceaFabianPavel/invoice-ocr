package com.invoiceocr.service;

import com.invoiceocr.domain.InvoiceData;
import java.nio.file.Path;

/**
 * The application's use case: given a document on disk, return structured data.
 *
 * <p>This is the only thing the presentation layer calls. Everything below it -
 * decoding, preprocessing, OCR, parsing - is an implementation detail.</p>
 */
@FunctionalInterface
public interface InvoiceRecognitionService {

    /**
     * @throws com.invoiceocr.exception.InvoiceOcrException if any stage of the pipeline fails
     */
    InvoiceData recognize(Path documentPath);
}
