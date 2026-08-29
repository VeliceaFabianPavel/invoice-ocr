package com.invoiceocr.exception;

/** Raised when a document cannot be read or decoded into an image. */
public class DocumentLoadException extends InvoiceOcrException {

    private static final long serialVersionUID = 1L;

    public DocumentLoadException(String message) {
        super(message);
    }

    public DocumentLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
