package com.invoiceocr.image;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.exception.DocumentLoadException;
import com.invoiceocr.exception.UnsupportedDocumentException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;

/** {@link DocumentLoader} built on {@link ImageIO}, restricted to the configured extensions. */
public final class ImageIoDocumentLoader implements DocumentLoader {

    private final OcrSettings settings;

    public ImageIoDocumentLoader(OcrSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean supports(Path path) {
        return path != null && settings.supportedExtensions().contains(extensionOf(path));
    }

    @Override
    public SourceImage load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!supports(path)) {
            throw new UnsupportedDocumentException("Unsupported document format: " + path.getFileName());
        }
        if (!Files.isReadable(path)) {
            throw new DocumentLoadException("File does not exist or cannot be read: " + path);
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new DocumentLoadException("No image decoder available for: " + path.getFileName());
            }
            return new SourceImage(path, image);
        } catch (IOException e) {
            throw new DocumentLoadException("Failed to read image: " + path.getFileName(), e);
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
