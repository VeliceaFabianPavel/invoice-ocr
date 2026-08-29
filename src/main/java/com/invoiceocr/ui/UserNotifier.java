package com.invoiceocr.ui;

/** Reports out-of-band messages (errors, warnings) to the user. */
public interface UserNotifier {

    void error(String title, String message);

    void warning(String title, String message);
}
