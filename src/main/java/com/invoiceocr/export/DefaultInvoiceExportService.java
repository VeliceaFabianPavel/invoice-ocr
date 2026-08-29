package com.invoiceocr.export;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.exception.ExportException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dispatches to the exporter registered for a format and writes the file.
 *
 * <p>Output goes to a temporary file next to the target and is moved into place
 * only after it is complete, so a failure halfway through never leaves a
 * truncated file where a good one used to be.</p>
 */
public final class DefaultInvoiceExportService implements InvoiceExportService {

    private static final Logger LOG = System.getLogger(DefaultInvoiceExportService.class.getName());
    private static final String TEMP_SUFFIX = ".part";

    private final Map<String, InvoiceExporter> exporters;

    public DefaultInvoiceExportService(List<InvoiceExporter> exporters) {
        Objects.requireNonNull(exporters, "exporters");
        this.exporters = exporters.stream().collect(Collectors.toMap(
                exporter -> exporter.format().id(),
                exporter -> exporter,
                (first, second) -> second,
                LinkedHashMap::new));
    }

    @Override
    public List<ExportFormat> supportedFormats() {
        return exporters.values().stream().map(InvoiceExporter::format).toList();
    }

    @Override
    public void export(InvoiceData data, Path target, ExportFormat format) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(format, "format");

        InvoiceExporter exporter = exporters.get(format.id());
        if (exporter == null) {
            throw new ExportException("No exporter is registered for format " + format.id());
        }

        Path directory = target.toAbsolutePath().getParent();
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            if (directory != null) {
                Files.createDirectories(directory);
            }
            try (OutputStream out = Files.newOutputStream(temporary)) {
                exporter.write(data, out);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            LOG.log(Level.INFO, () -> "Exported " + format.id() + " to " + target);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw new ExportException("Could not write " + target.getFileName() + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteQuietly(temporary);
            throw e;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.log(Level.DEBUG, () -> "Could not remove the partial file " + path);
        }
    }
}
