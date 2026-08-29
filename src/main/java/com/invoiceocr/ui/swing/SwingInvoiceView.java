package com.invoiceocr.ui.swing;

import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import com.invoiceocr.ui.InvoiceView;
import com.invoiceocr.ui.InvoiceViewListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;

/**
 * Swing implementation of {@link InvoiceView}.
 *
 * <p>Builds widgets, forwards clicks as intents and renders whatever strings it
 * is given. It performs no OCR, no parsing and no formatting, which is why it
 * needs no unit tests of its own.</p>
 */
public final class SwingInvoiceView implements InvoiceView {

    private static final Dimension WINDOW_SIZE = new Dimension(1180, 720);
    private static final Font MONOSPACED = new Font("Consolas", Font.PLAIN, 13);
    private static final Font PROPORTIONAL = new Font("Segoe UI", Font.PLAIN, 14);

    private final MessageSource messages;
    private final List<InvoiceViewListener> listeners = new ArrayList<>();

    private final JFrame frame;
    private final JButton loadButton;
    private final JButton exportButton;
    private final JButton clearButton;

    private boolean exportAvailable;
    private boolean busy;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final TitledTextPanel rawPanel;
    private final TitledTextPanel reportPanel;

    public SwingInvoiceView(MessageSource messages) {
        this.messages = Objects.requireNonNull(messages, "messages");

        this.loadButton = createButton(MessageKeys.ACTION_LOAD, InvoiceViewListener::onLoadInvoiceRequested);
        this.loadButton.setToolTipText(messages.get(MessageKeys.ACTION_LOAD_TOOLTIP));
        this.exportButton = createButton(MessageKeys.ACTION_EXPORT, InvoiceViewListener::onExportRequested);
        this.exportButton.setToolTipText(messages.get(MessageKeys.ACTION_EXPORT_TOOLTIP));
        this.exportButton.setEnabled(false);
        this.clearButton = createButton(MessageKeys.ACTION_CLEAR, InvoiceViewListener::onClearRequested);

        this.statusLabel = new JLabel(messages.get(MessageKeys.STATUS_READY));
        this.progressBar = new JProgressBar();
        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        this.progressBar.setPreferredSize(new Dimension(160, 16));

        this.rawPanel = new TitledTextPanel(messages.get(MessageKeys.PANEL_RAW_TEXT), MONOSPACED);
        this.reportPanel = new TitledTextPanel(messages.get(MessageKeys.PANEL_STRUCTURED), PROPORTIONAL);

        this.frame = createFrame();
    }

    private JButton createButton(String labelKey, Consumer<InvoiceViewListener> intent) {
        JButton button = new JButton(messages.get(labelKey));
        button.setPreferredSize(new Dimension(170, 34));
        button.addActionListener(event -> listeners.forEach(intent));
        return button;
    }

    private JFrame createFrame() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rawPanel, reportPanel);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());

        JFrame window = new JFrame(messages.get(MessageKeys.APP_TITLE));
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setLayout(new BorderLayout());
        window.add(createToolbar(), BorderLayout.NORTH);
        window.add(split, BorderLayout.CENTER);
        window.add(createStatusBar(), BorderLayout.SOUTH);
        window.setSize(WINDOW_SIZE);
        window.setMinimumSize(new Dimension(720, 480));
        window.setLocationRelativeTo(null);
        return window;
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        toolbar.add(loadButton);
        toolbar.add(exportButton);
        toolbar.add(clearButton);
        toolbar.add(progressBar);
        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);
        return statusBar;
    }

    /** The window itself, for dialogs that need a parent component. */
    public Component rootComponent() {
        return frame;
    }

    @Override
    public void addListener(InvoiceViewListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void showRawText(String rawText) {
        rawPanel.setText(rawText);
    }

    @Override
    public void showReport(String report) {
        reportPanel.setText(report);
    }

    @Override
    public void showStatus(String status) {
        statusLabel.setText(status);
    }

    @Override
    public void setBusy(boolean busy) {
        this.busy = busy;
        loadButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        exportButton.setEnabled(exportAvailable && !busy);
        progressBar.setVisible(busy);
        frame.setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void setExportEnabled(boolean enabled) {
        this.exportAvailable = enabled;
        exportButton.setEnabled(enabled && !busy);
    }

    @Override
    public void clear() {
        rawPanel.setText("");
        reportPanel.setText("");
    }

    @Override
    public void display() {
        frame.setVisible(true);
    }
}
