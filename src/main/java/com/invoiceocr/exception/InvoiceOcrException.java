package com.invoiceocr.exception;

/**
 * Base type for every failure raised by this application.
 *
 * <p>All application errors are unchecked so that they can flow freely through
 * functional interfaces (suppliers, callables, listeners) without forcing every
 * layer to declare or wrap them. Layer boundaries translate foreign exceptions
 * into one of the subtypes so the presentation layer only ever has to reason
 * about this hierarchy.</p>
 */
public abstract class InvoiceOcrException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected InvoiceOcrException(String message) {
        super(message);
    }

    protected InvoiceOcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
