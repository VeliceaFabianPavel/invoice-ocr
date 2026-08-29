package com.invoiceocr.extraction;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;

/** Turns raw OCR text into structured invoice data. */
@FunctionalInterface
public interface InvoiceParser {

    InvoiceData parse(RecognizedText text);
}
