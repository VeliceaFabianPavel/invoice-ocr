package com.invoiceocr.ui.swing;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import com.invoiceocr.ui.DocumentChooser;
import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * File selection dialog whose filter is derived from the configured extensions,
 * so the chooser and the loader can never disagree about what is supported.
 */
public final class SwingDocumentChooser implements DocumentChooser {

    private final OcrSettings settings;
    private final MessageSource messages;
    private final Supplier<Component> parentSupplier;

    private File lastDirectory;

    public SwingDocumentChooser(OcrSettings settings, MessageSource messages, Supplier<Component> parentSupplier) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.parentSupplier = Objects.requireNonNull(parentSupplier, "parentSupplier");
    }

    @Override
    public Optional<Path> choose() {
        List<String> extensions = settings.supportedExtensions();
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(messages.get(MessageKeys.CHOOSER_TITLE));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
                messages.get(MessageKeys.CHOOSER_FILTER, String.join(", ", extensions)),
                extensions.toArray(String[]::new)));

        if (chooser.showOpenDialog(parentSupplier.get()) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        File selected = chooser.getSelectedFile();
        lastDirectory = selected.getParentFile();
        return Optional.of(selected.toPath());
    }
}
