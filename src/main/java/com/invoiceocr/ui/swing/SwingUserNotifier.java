package com.invoiceocr.ui.swing;

import com.invoiceocr.ui.UserNotifier;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/** Shows notifications as modal dialogs anchored to the main window. */
public final class SwingUserNotifier implements UserNotifier {

    private final Supplier<Component> parentSupplier;

    public SwingUserNotifier(Supplier<Component> parentSupplier) {
        this.parentSupplier = Objects.requireNonNull(parentSupplier, "parentSupplier");
    }

    @Override
    public void error(String title, String message) {
        show(title, message, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void warning(String title, String message) {
        show(title, message, JOptionPane.WARNING_MESSAGE);
    }

    private void show(String title, String message, int type) {
        JOptionPane.showMessageDialog(parentSupplier.get(), message, title, type);
    }
}
