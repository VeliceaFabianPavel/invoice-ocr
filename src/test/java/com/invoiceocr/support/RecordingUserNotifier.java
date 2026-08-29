package com.invoiceocr.support;

import com.invoiceocr.ui.UserNotifier;
import java.util.ArrayList;
import java.util.List;

/** Fake notifier that records the messages a real UI would have shown in a dialog. */
public final class RecordingUserNotifier implements UserNotifier {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    @Override
    public void error(String title, String message) {
        errors.add(message);
    }

    @Override
    public void warning(String title, String message) {
        warnings.add(message);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
