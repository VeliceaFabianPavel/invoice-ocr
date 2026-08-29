package com.invoiceocr.domain;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A decoded document image together with the file it came from.
 *
 * <p>Keeping the origin next to the pixels lets logging, error messages and
 * preprocessing filters stay informative without a second parameter travelling
 * alongside the image everywhere.</p>
 */
public record SourceImage(Path origin, BufferedImage image) {

    public SourceImage {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(image, "image");
    }

    /** Returns a copy of this document pointing at a transformed image. */
    public SourceImage withImage(BufferedImage transformed) {
        return new SourceImage(origin, transformed);
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }
}
