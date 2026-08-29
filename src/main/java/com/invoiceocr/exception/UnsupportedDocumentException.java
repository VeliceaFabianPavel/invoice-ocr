package com.invoiceocr.exception;

/** Raised when a document has a format the loader does not handle. */
public class UnsupportedDocumentException extends DocumentLoadException {

    private static final long serialVersionUID = 1L;

    public UnsupportedDocumentException(String message) {
        super(message);
    }
}
