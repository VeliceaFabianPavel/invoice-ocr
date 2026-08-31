package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.image.BufferedImage;

/**
 * Pulls a flat, washed-out page back to full black and full white.
 *
 * <p>A faded thermal receipt or an under-exposed photograph occupies a narrow
 * band of the grey scale — say 90 to 170 — and everything Tesseract does
 * afterwards has to work with that. Rescaling the band it actually uses onto the
 * full range costs one pass over the pixels and gives the binariser something to
 * work with.</p>
 *
 * <p>The band is measured by percentile rather than by minimum and maximum, so a
 * single black speck of dust or one blown-out highlight cannot define the range
 * for the whole page. A page that already spans the scale is returned untouched.</p>
 */
public final class ContrastStretchPreprocessor implements ImagePreprocessor {

    private static final int LEVELS = 256;

    /** Share of pixels ignored at each end when deciding what the page's range is. */
    private static final double TAIL_FRACTION = 0.005;

    /** Narrower than this and there is nothing to stretch; wider and it is already spread. */
    private static final int MINIMUM_SPAN = 8;
    private static final int ALREADY_STRETCHED_SPAN = 230;

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage gray = asGray(source.image());
        int width = gray.getWidth();
        int height = gray.getHeight();
        if (width == 0 || height == 0) {
            return source;
        }

        int[] histogram = new int[LEVELS];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                histogram[gray.getRaster().getSample(x, y, 0)]++;
            }
        }

        long tail = Math.round((long) width * height * TAIL_FRACTION);
        int low = percentile(histogram, tail, true);
        int high = percentile(histogram, tail, false);
        int span = high - low;
        if (span < MINIMUM_SPAN || span >= ALREADY_STRETCHED_SPAN) {
            return source;
        }

        int[] lookup = new int[LEVELS];
        for (int level = 0; level < LEVELS; level++) {
            int stretched = (int) Math.round((level - low) * 255.0 / span);
            lookup[level] = Math.max(0, Math.min(255, stretched));
        }

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output.getRaster().setSample(x, y, 0, lookup[gray.getRaster().getSample(x, y, 0)]);
            }
        }
        return source.withImage(output);
    }

    /** The level at which {@code tail} pixels have been counted from one end. */
    private static int percentile(int[] histogram, long tail, boolean fromDark) {
        long seen = 0;
        if (fromDark) {
            for (int level = 0; level < LEVELS; level++) {
                seen += histogram[level];
                if (seen > tail) {
                    return level;
                }
            }
            return 0;
        }
        for (int level = LEVELS - 1; level >= 0; level--) {
            seen += histogram[level];
            if (seen > tail) {
                return level;
            }
        }
        return LEVELS - 1;
    }

    private static BufferedImage asGray(BufferedImage image) {
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
