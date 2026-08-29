package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Enlarges small scans so glyphs reach the ~30 px height Tesseract prefers.
 *
 * <p>A minimum width of {@code 0} (or an image already wide enough) makes this a
 * no-op, so it is always safe to leave in the pipeline.</p>
 */
public final class UpscalePreprocessor implements ImagePreprocessor {

    private static final double MAX_SCALE = 4.0;

    private final int minimumWidth;

    public UpscalePreprocessor(int minimumWidth) {
        if (minimumWidth < 0) {
            throw new IllegalArgumentException("minimumWidth must not be negative");
        }
        this.minimumWidth = minimumWidth;
    }

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage original = source.image();
        if (minimumWidth == 0 || original.getWidth() >= minimumWidth) {
            return source;
        }
        double scale = Math.min(MAX_SCALE, (double) minimumWidth / original.getWidth());
        int width = (int) Math.round(original.getWidth() * scale);
        int height = (int) Math.round(original.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(width, height, imageTypeOf(original));
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return source.withImage(scaled);
    }

    private static int imageTypeOf(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : image.getType();
    }
}
