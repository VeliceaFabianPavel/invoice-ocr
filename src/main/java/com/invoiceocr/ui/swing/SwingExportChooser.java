package com.invoiceocr.ui.swing;

import com.invoiceocr.export.ExportFormat;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import com.invoiceocr.ui.ExportChooser;
import com.invoiceocr.ui.ExportRequest;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Save dialog offering one filter per export format.
 *
 * <p>The selected filter decides the format, and the extension is corrected to
 * match, so choosing "PDF document" and typing {@code report} yields
 * {@code report.pdf} rather than an extensionless file. Changing the filter
 * rewrites the extension in the name field too, which is what people expect
 * from a save dialog.</p>
 */
public final class SwingExportChooser implements ExportChooser {

    private final List<ExportFormat> formats;
    private final MessageSource messages;
    private final Supplier<Component> parentSupplier;

    private File lastDirectory;

    public SwingExportChooser(List<ExportFormat> formats, MessageSource messages,
                              Supplier<Component> parentSupplier) {
        this.formats = List.copyOf(Objects.requireNonNull(formats, "formats"));
        this.messages = Objects.requireNonNull(messages, "messages");
        this.parentSupplier = Objects.requireNonNull(parentSupplier, "parentSupplier");
        if (this.formats.isEmpty()) {
            throw new IllegalArgumentException("At least one export format is required");
        }
    }

    @Override
    public Optional<ExportRequest> choose(String suggestedName, ExportFormat preselected) {
        Objects.requireNonNull(suggestedName, "suggestedName");
        ExportFormat initial = formats.contains(preselected) ? preselected : formats.get(0);

        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(messages.get(MessageKeys.CHOOSER_EXPORT_TITLE));
        chooser.setApproveButtonText(messages.get(MessageKeys.CHOOSER_EXPORT_APPROVE));
        chooser.setAcceptAllFileFilterUsed(false);

        Map<FileFilter, ExportFormat> byFilter = new HashMap<>();
        for (ExportFormat format : formats) {
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    messages.get(format.labelKey()) + " (*." + format.extension() + ")", format.extension());
            byFilter.put(filter, format);
            chooser.addChoosableFileFilter(filter);
            if (format.equals(initial)) {
                chooser.setFileFilter(filter);
            }
        }
        chooser.setSelectedFile(new File(initial.withExtension(suggestedName)));

        // Keep the typed name in step with the chosen format.
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, event -> {
            ExportFormat selected = byFilter.get(chooser.getFileFilter());
            File current = chooser.getSelectedFile();
            if (selected != null && current != null) {
                chooser.setSelectedFile(new File(stripExtension(current.getName()) + "." + selected.extension()));
            }
        });

        while (chooser.showSaveDialog(parentSupplier.get()) == JFileChooser.APPROVE_OPTION) {
            ExportFormat format = byFilter.getOrDefault(chooser.getFileFilter(), initial);
            File selected = chooser.getSelectedFile();
            File target = new File(selected.getParentFile(), format.withExtension(selected.getName()));
            lastDirectory = target.getParentFile();

            if (!Files.exists(target.toPath()) || confirmOverwrite(target)) {
                return Optional.of(new ExportRequest(target.toPath(), format));
            }
        }
        return Optional.empty();
    }

    private boolean confirmOverwrite(File target) {
        int answer = JOptionPane.showConfirmDialog(
                parentSupplier.get(),
                messages.get(MessageKeys.CHOOSER_OVERWRITE_QUESTION, target.getName()),
                messages.get(MessageKeys.CHOOSER_OVERWRITE_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
