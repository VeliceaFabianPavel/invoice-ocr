package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.image.BufferedImage;

/**
 * Turns the page black and white, judging every pixel against its own
 * neighbourhood rather than against the page as a whole.
 *
 * <p>A phone photograph of an invoice is never evenly lit. One corner is in
 * shadow, the opposite one catches the lamp, and any single cut-off that keeps
 * the bright half readable turns the dark half into a solid block. Comparing a
 * pixel with the average of the window around it removes the lighting entirely,
 * because a shadow moves a pixel and its neighbours together.</p>
 *
 * <p>The window average is taken from an integral image, so the cost does not
 * grow with the window: two additions and two subtractions per pixel, whatever
 * the radius. A page-sized window would be a global threshold; a character-sized
 * one would erase the strokes; the default sits between them, near the height of
 * a line of text.</p>
 */
public final class AdaptiveThresholdPreprocessor implements ImagePreprocessor {

    /** Window side as a fraction of the page width, and the bounds it is kept inside. */
    private static final double WINDOW_FRACTION = 0.06;
    private static final int MINIMUM_WINDOW = 15;
    private static final int MAXIMUM_WINDOW = 121;

    /**
     * How far below its neighbourhood a pixel must sit to count as ink, as a
     * fraction. Without it, an empty region of paper is half black: with nothing
     * but noise in the window, the mean is the noise.
     */
    private static final double MARGIN = 0.85;

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage gray = toGray(source.image());
        int width = gray.getWidth();
        int height = gray.getHeight();
        if (width == 0 || height == 0) {
            return source;
        }

        long[] integral = integralOf(gray, width, height);
        int radius = radiusFor(width);

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            int top = Math.max(0, y - radius);
            int bottom = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                int right = Math.min(width - 1, x + radius);
                long area = (long) (right - left + 1) * (bottom - top + 1);
                long sum = windowSum(integral, width, left, top, right, bottom);
                double mean = (double) sum / area;
                int level = gray.getRaster().getSample(x, y, 0);
                output.getRaster().setSample(x, y, 0, level < mean * MARGIN ? 0 : 255);
            }
        }
        return source.withImage(output);
    }

    private static int radiusFor(int width) {
        int window = (int) Math.round(width * WINDOW_FRACTION);
        window = Math.max(MINIMUM_WINDOW, Math.min(MAXIMUM_WINDOW, window));
        return Math.max(1, window / 2);
    }

    /** Row-major prefix sums with a leading zero row and column, so no bounds test is needed. */
    private static long[] integralOf(BufferedImage gray, int width, int height) {
        long[] integral = new long[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            long rowSum = 0;
            for (int x = 0; x < width; x++) {
                rowSum += gray.getRaster().getSample(x, y, 0);
                integral[(y + 1) * (width + 1) + x + 1] =
                        integral[y * (width + 1) + x + 1] + rowSum;
            }
        }
        return integral;
    }

    private static long windowSum(long[] integral, int width, int left, int top, int right, int bottom) {
        int stride = width + 1;
        return integral[(bottom + 1) * stride + right + 1]
                - integral[top * stride + right + 1]
                - integral[(bottom + 1) * stride + left]
                + integral[top * stride + left];
    }

    private static BufferedImage toGray(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage gray = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        var graphics = gray.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return gray;
    }
}
