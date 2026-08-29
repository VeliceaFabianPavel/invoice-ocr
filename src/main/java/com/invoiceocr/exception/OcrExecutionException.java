package com.invoiceocr.exception;

/** Raised when the OCR engine fails or returns nothing usable. */
public class OcrExecutionException extends InvoiceOcrException {

    private static final long serialVersionUID = 1L;

    public OcrExecutionException(String message) {
        super(message);
    }

    public OcrExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
