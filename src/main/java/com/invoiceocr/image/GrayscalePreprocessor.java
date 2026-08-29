package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Converts the page to 8-bit grayscale, which Tesseract binarises more reliably. */
public final class GrayscalePreprocessor implements ImagePreprocessor {

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage original = source.image();
        if (original.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return source;
        }
        BufferedImage gray = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = gray.createGraphics();
        try {
            graphics.drawImage(original, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return source.withImage(gray);
    }
}
