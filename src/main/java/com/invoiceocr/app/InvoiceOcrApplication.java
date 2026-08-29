package com.invoiceocr.app;

import com.invoiceocr.ui.InvoiceView;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point.
 *
 * <p>Its whole job is to start Swing correctly and hand control to the
 * {@link ApplicationAssembler}; no business logic lives here.</p>
 */
public final class InvoiceOcrApplication {

    private static final Logger LOG = System.getLogger(InvoiceOcrApplication.class.getName());

    public static void main(String[] args) {
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> start(new ApplicationAssembler()));
    }

    private static void start(ApplicationAssembler assembler) {
        try {
            InvoiceView view = assembler.assemble();
            view.display();
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Application failed to start", e);
            JOptionPane.showMessageDialog(
                    null, e.getMessage(), "Invoice OCR", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** A failure here is cosmetic, so it is logged and the default look and feel stays. */
    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedOperationException
                 | javax.swing.UnsupportedLookAndFeelException e) {
            LOG.log(Level.WARNING, "Falling back to the default look and feel", e);
        }
    }

    private InvoiceOcrApplication() {
        throw new AssertionError("No instances");
    }
}
