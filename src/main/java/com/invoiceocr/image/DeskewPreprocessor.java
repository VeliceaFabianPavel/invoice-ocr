package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Straightens a page that was scanned or photographed at an angle.
 *
 * <p>Tesseract assumes text runs horizontally. A page tilted by two or three
 * degrees — which is what putting paper on a flatbed by hand produces — costs a
 * noticeable amount of accuracy, and a page tilted by five costs a great deal:
 * the line finder starts merging neighbouring lines, and merged lines are where
 * a total ends up glued to the label of the row above it.</p>
 *
 * <p>The angle is found by projection profiling. Ink pixels are counted into
 * rows for a range of candidate angles, and the angle whose row counts are the
 * most uneven wins — because text that lines up puts all of its ink into a few
 * rows and leaves the gaps between lines empty, while text that does not spreads
 * it evenly.</p>
 *
 * <pre>
 *   straight:  ▁▁███▁▁███▁▁███▁▁   high variance, lines are distinct
 *   tilted:    ▃▄▅▆▅▄▅▆▅▄▅▆▅▄▅▆▃   low variance, lines smear together
 * </pre>
 *
 * <p>Profiling runs on a downscaled copy — the angle of a page does not change
 * with its resolution, and a small copy makes the search cheap enough to leave
 * switched on by default.</p>
 */
public final class DeskewPreprocessor implements ImagePreprocessor {

    /** Beyond this the page is not tilted, it is rotated, and that is a different problem. */
    private static final double MAXIMUM_ANGLE = 8.0;

    /** Coarse sweep first, then a fine one around the winner. */
    private static final double COARSE_STEP = 0.5;
    private static final double FINE_STEP = 0.1;
    private static final double FINE_RANGE = 0.6;

    /** Below this the correction is not worth the resampling it costs. */
    private static final double MINIMUM_CORRECTION = 0.25;

    /** Width the profiling copy is reduced to; enough to see lines, cheap to sweep. */
    private static final int ANALYSIS_WIDTH = 600;

    /** How far below the mean a pixel must be before it counts as ink. */
    private static final double INK_MARGIN = 0.92;

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage original = source.image();
        boolean[][] ink = inkMask(original);
        if (ink.length == 0) {
            return source;
        }
        double angle = estimateAngle(ink);
        if (Math.abs(angle) < MINIMUM_CORRECTION) {
            return source;
        }
        return source.withImage(rotate(original, -angle));
    }

    /** The angle, in degrees, that the page appears to be rotated by. */
    double estimateAngle(boolean[][] ink) {
        double best = 0.0;
        double bestScore = -1.0;
        for (double angle = -MAXIMUM_ANGLE; angle <= MAXIMUM_ANGLE; angle += COARSE_STEP) {
            double score = alignmentScore(ink, angle);
            if (score > bestScore) {
                bestScore = score;
                best = angle;
            }
        }
        double coarse = best;
        for (double angle = coarse - FINE_RANGE; angle <= coarse + FINE_RANGE; angle += FINE_STEP) {
            double score = alignmentScore(ink, angle);
            if (score > bestScore) {
                bestScore = score;
                best = angle;
            }
        }
        return best;
    }

    /**
     * How concentrated the ink is once the page is read at {@code degrees}.
     *
     * <p>The sum of squared row counts is the measure. Squaring is what makes it
     * work: spreading the same amount of ink over more rows always lowers it, so
     * the maximum is where the lines are sharpest.</p>
     */
    private static double alignmentScore(boolean[][] ink, double degrees) {
        int height = ink.length;
        int width = ink[0].length;
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        // Row index of a pixel once the page is read at this angle. The minus
        // sign is the same one that appears in a rotation by -degrees: the score
        // is highest where the tilt has been undone, so a page tilted clockwise
        // has to score highest at a positive angle for the correction that
        // follows to turn it back the right way.
        int span = height + (int) Math.ceil(width * Math.abs(sin)) + 2;
        int[] rows = new int[span];
        int offset = (int) Math.ceil(width * Math.max(0.0, sin)) + 1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ink[y][x]) {
                    int row = (int) Math.round(y * cos - x * sin) + offset;
                    if (row >= 0 && row < span) {
                        rows[row]++;
                    }
                }
            }
        }
        double score = 0.0;
        for (int count : rows) {
            score += (double) count * count;
        }
        return score;
    }

    /**
     * A small binary view of the page: true where there is ink.
     *
     * <p>Thresholded against the page mean rather than a fixed level, so a grey
     * photograph and a clean white scan both reduce to the same thing.</p>
     */
    static boolean[][] inkMask(BufferedImage image) {
        int width = Math.min(ANALYSIS_WIDTH, image.getWidth());
        if (width <= 0 || image.getHeight() <= 0) {
            return new boolean[0][0];
        }
        double scale = (double) width / image.getWidth();
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = small.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        long sum = 0;
        int[] levels = new int[width * height];
        for (int y = 0, i = 0; y < height; y++) {
            for (int x = 0; x < width; x++, i++) {
                int level = small.getRaster().getSample(x, y, 0);
                levels[i] = level;
                sum += level;
            }
        }
        double threshold = (double) sum / levels.length * INK_MARGIN;

        boolean[][] mask = new boolean[height][width];
        for (int y = 0, i = 0; y < height; y++) {
            for (int x = 0; x < width; x++, i++) {
                mask[y][x] = levels[i] < threshold;
            }
        }
        return mask;
    }

    /** Rotates about the centre onto a white ground, keeping the original size. */
    private static BufferedImage rotate(BufferedImage image, double degrees) {
        BufferedImage rotated = new BufferedImage(
                image.getWidth(), image.getHeight(), imageTypeOf(image));
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rotated.getWidth(), rotated.getHeight());
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setTransform(AffineTransform.getRotateInstance(
                    Math.toRadians(degrees), image.getWidth() / 2.0, image.getHeight() / 2.0));
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    private static int imageTypeOf(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_RGB
                : image.getType();
    }
}
