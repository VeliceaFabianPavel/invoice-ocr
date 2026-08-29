package com.invoiceocr.ocr.tesseract;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.exception.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Verifies that a usable Tesseract data directory is present.
 *
 * <p>The original C++ program only reported "cannot initialise Tesseract"; this
 * check names the directory, the language and the files that are actually
 * there, which is the difference between a five-second and a one-hour fix.</p>
 */
public final class TesseractInstallation {

    private static final String TRAINED_DATA_SUFFIX = ".traineddata";

    private final OcrSettings settings;

    public TesseractInstallation(OcrSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** @throws ConfigurationException if tessdata or the requested language is missing */
    public void verify() {
        Path tessdata = settings.tessdataPath();
        if (!Files.isDirectory(tessdata)) {
            throw new ConfigurationException("tessdata directory not found: " + tessdata);
        }
        for (String language : languages()) {
            Path trainedData = tessdata.resolve(language + TRAINED_DATA_SUFFIX);
            if (!Files.isRegularFile(trainedData)) {
                throw new ConfigurationException("Language file " + language + TRAINED_DATA_SUFFIX
                        + " not found in " + tessdata + ". Available: " + available(tessdata));
            }
        }
    }

    private List<String> languages() {
        return Stream.of(settings.language().split("\\+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String available(Path tessdata) {
        try (Stream<Path> files = Files.list(tessdata)) {
            List<String> names = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(TRAINED_DATA_SUFFIX))
                    .sorted()
                    .toList();
            return names.isEmpty() ? "none" : String.join(", ", names);
        } catch (IOException e) {
            return "unreadable (" + e.getMessage() + ")";
        }
    }
}
