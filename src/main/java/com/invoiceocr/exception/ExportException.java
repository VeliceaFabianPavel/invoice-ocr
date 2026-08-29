package com.invoiceocr.exception;

/** Raised when extracted data cannot be written to a file. */
public class ExportException extends InvoiceOcrException {

    private static final long serialVersionUID = 1L;

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
