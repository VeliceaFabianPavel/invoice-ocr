package com.invoiceocr.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.SourceImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The image passes, driven with pages drawn on the fly.
 *
 * <p>Everything here is generated rather than loaded, so the suite still needs
 * no image files and no native library — and a page whose skew and lighting are
 * known exactly is a far better test than a photograph whose are not.</p>
 */
@DisplayName("Image preprocessing")
class ImagePreprocessorsTest {

    // ------------------------------------------------------------ fixtures

    /** A white page with evenly spaced black bars: text lines, near enough. */
    private static BufferedImage ruledPage(int width, int height, double skewDegrees) {
        BufferedImage page = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = page.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            if (skewDegrees != 0.0) {
                graphics.setTransform(AffineTransform.getRotateInstance(
                        Math.toRadians(skewDegrees), width / 2.0, height / 2.0));
            }
            graphics.setColor(Color.BLACK);
            for (int y = 20; y < height - 20; y += 20) {
                graphics.fillRect(width / 8, y, width * 3 / 4, 6);
            }
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private static SourceImage source(BufferedImage image) {
        return new SourceImage(Path.of("page.png"), image);
    }

    /** A page lit unevenly: a bright left half and a dark right half, with ink on both. */
    private static BufferedImage unevenlyLitPage(int width, int height) {
        BufferedImage page = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int background = x < width / 2 ? 240 : 90;
                boolean ink = (y % 20) < 6 && x % 17 < 8;
                page.getRaster().setSample(x, y, 0, ink ? background - 60 : background);
            }
        }
        return page;
    }

    private static long inkCount(BufferedImage image) {
        long dark = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRaster().getSample(x, y, 0) < 128) {
                    dark++;
                }
            }
        }
        return dark;
    }

    // --------------------------------------------------------------- tests

    @Nested
    @DisplayName("Deskew")
    class Deskew {

        private final DeskewPreprocessor deskew = new DeskewPreprocessor();

        @Test
        @DisplayName("measures the angle a page was scanned at")
        void measuresTheSkew() {
            double estimated = deskew.estimateAngle(
                    DeskewPreprocessor.inkMask(ruledPage(400, 400, 3.0)));

            assertTrue(Math.abs(estimated - 3.0) < 0.6,
                    () -> "expected about 3 degrees but measured " + estimated);
        }

        @Test
        @DisplayName("measures a tilt the other way just as well")
        void measuresNegativeSkew() {
            double estimated = deskew.estimateAngle(
                    DeskewPreprocessor.inkMask(ruledPage(400, 400, -2.0)));

            assertTrue(Math.abs(estimated + 2.0) < 0.6,
                    () -> "expected about -2 degrees but measured " + estimated);
        }

        @Test
        @DisplayName("a straight page is returned untouched rather than resampled")
        void leavesAStraightPageAlone() {
            SourceImage straight = source(ruledPage(400, 400, 0.0));
            assertSame(straight, deskew.apply(straight));
        }

        @Test
        @DisplayName("straightening a tilted page makes its lines line up again")
        void straightensATiltedPage() {
            SourceImage corrected = deskew.apply(source(ruledPage(400, 400, 3.0)));

            double residual = deskew.estimateAngle(DeskewPreprocessor.inkMask(corrected.image()));
            assertTrue(Math.abs(residual) < 0.6,
                    () -> "the corrected page is still tilted by " + residual);
        }

        @Test
        @DisplayName("the page keeps its size, so nothing downstream has to care")
        void preservesTheSize() {
            SourceImage corrected = deskew.apply(source(ruledPage(400, 300, 4.0)));

            assertEquals(400, corrected.width());
            assertEquals(300, corrected.height());
        }

        @Test
        @DisplayName("a blank page has no angle to find and is left alone")
        void survivesABlankPage() {
            SourceImage blank = source(new BufferedImage(50, 50, BufferedImage.TYPE_BYTE_GRAY));
            assertEquals(50, deskew.apply(blank).width());
        }
    }

    @Nested
    @DisplayName("Adaptive threshold")
    class AdaptiveThreshold {

        private final AdaptiveThresholdPreprocessor threshold = new AdaptiveThresholdPreprocessor();

        @Test
        @DisplayName("produces a page of pure black and pure white")
        void producesTwoLevels() {
            BufferedImage output = threshold.apply(source(ruledPage(200, 200, 0.0))).image();

            for (int y = 0; y < output.getHeight(); y++) {
                for (int x = 0; x < output.getWidth(); x++) {
                    int level = output.getRaster().getSample(x, y, 0);
                    assertTrue(level == 0 || level == 255, "unexpected level " + level);
                }
            }
        }

        @Test
        @DisplayName("keeps the ink in the shadowed half, which a single cut-off would lose")
        void survivesUnevenLighting() {
            BufferedImage output = threshold.apply(source(unevenlyLitPage(240, 200))).image();

            long left = 0;
            long right = 0;
            for (int y = 0; y < output.getHeight(); y++) {
                for (int x = 0; x < output.getWidth(); x++) {
                    if (output.getRaster().getSample(x, y, 0) == 0) {
                        if (x < output.getWidth() / 2) {
                            left++;
                        } else {
                            right++;
                        }
                    }
                }
            }
            long bright = left;
            long shadowed = right;
            assertTrue(bright > 0 && shadowed > 0,
                    () -> "ink on the bright half: " + bright + ", on the dark half: " + shadowed);
            assertTrue(Math.abs(bright - shadowed) < Math.max(bright, shadowed),
                    "both halves should keep a comparable amount of ink");
        }

        @Test
        @DisplayName("an entirely blank page does not turn half black")
        void doesNotThresholdNoise() {
            BufferedImage blank = new BufferedImage(60, 60, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = blank.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 60, 60);
            graphics.dispose();

            assertEquals(0, inkCount(threshold.apply(source(blank)).image()));
        }
    }

    @Nested
    @DisplayName("Contrast stretch")
    class ContrastStretch {

        private final ContrastStretchPreprocessor stretch = new ContrastStretchPreprocessor();

        @Test
        @DisplayName("spreads a flat page back over the full range")
        void spreadsAFlatPage() {
            BufferedImage flat = new BufferedImage(40, 40, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    flat.getRaster().setSample(x, y, 0, x < 20 ? 100 : 160);
                }
            }

            BufferedImage output = stretch.apply(source(flat)).image();

            assertEquals(0, output.getRaster().getSample(0, 0, 0));
            assertEquals(255, output.getRaster().getSample(39, 0, 0));
        }

        @Test
        @DisplayName("a page that already spans the range is returned untouched")
        void leavesAGoodPageAlone() {
            SourceImage good = source(ruledPage(120, 120, 0.0));
            assertSame(good, stretch.apply(good));
        }

        @Test
        @DisplayName("a page of one single level has nothing to stretch")
        void leavesAUniformPageAlone() {
            SourceImage uniform = source(new BufferedImage(30, 30, BufferedImage.TYPE_BYTE_GRAY));
            assertSame(uniform, stretch.apply(uniform));
        }
    }

    @Nested
    @DisplayName("Sharpen")
    class Sharpen {

        private final SharpenPreprocessor sharpen = new SharpenPreprocessor();

        @Test
        @DisplayName("raises the contrast across an edge")
        void sharpensAnEdge() {
            BufferedImage soft = new BufferedImage(30, 30, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < 30; y++) {
                for (int x = 0; x < 30; x++) {
                    soft.getRaster().setSample(x, y, 0, Math.min(255, x * 8));
                }
            }

            BufferedImage output = sharpen.apply(source(soft)).image();

            int before = soft.getRaster().getSample(16, 15, 0) - soft.getRaster().getSample(14, 15, 0);
            int after = output.getRaster().getSample(16, 15, 0) - output.getRaster().getSample(14, 15, 0);
            assertTrue(after >= before, "sharpening should not soften an edge");
        }

        @Test
        @DisplayName("keeps the page the same size")
        void preservesTheSize() {
            SourceImage output = sharpen.apply(source(ruledPage(80, 60, 0.0)));

            assertEquals(80, output.width());
            assertEquals(60, output.height());
        }

        @Test
        @DisplayName("an image too small to convolve is returned untouched")
        void survivesATinyImage() {
            SourceImage tiny = source(new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY));
            assertSame(tiny, sharpen.apply(tiny));
        }
    }
}
