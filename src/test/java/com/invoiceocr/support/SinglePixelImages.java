package com.invoiceocr.support;

import com.invoiceocr.domain.SourceImage;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Tiny images for pipeline tests that never look at pixels. */
public final class SinglePixelImages {

    public static SourceImage of(String fileName) {
        return of(fileName, 1, 1);
    }

    public static SourceImage of(String fileName, int width, int height) {
        return new SourceImage(Path.of(fileName), new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }

    private SinglePixelImages() {
        throw new AssertionError("No instances");
    }
}
