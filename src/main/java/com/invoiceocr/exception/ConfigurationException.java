package com.invoiceocr.exception;

/** Raised when the application is misconfigured (missing tessdata, bad values, ...). */
public class ConfigurationException extends InvoiceOcrException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
