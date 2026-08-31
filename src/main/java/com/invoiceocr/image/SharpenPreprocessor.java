package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * Puts an edge back on characters that a scanner or a lens softened.
 *
 * <p>Upscaling a small scan interpolates, and interpolation blurs. So does a
 * phone camera at close range. Either way the stems of the letters lose their
 * edges, and a blurred {@code 8} and a blurred {@code 6} are the same handful of
 * grey pixels — which is where the digit confusions the patterns have to tolerate
 * come from in the first place.</p>
 *
 * <p>A mild sharpen is the right amount. A strong one turns paper grain into
 * speckle, and speckle costs more than softness does, so the kernel here is
 * deliberately gentle and the pass is one variant among several rather than
 * something applied to every page.</p>
 */
public final class SharpenPreprocessor implements ImagePreprocessor {

    /** Centre weight of the 3×3 kernel; the four neighbours share the remainder. */
    private static final float CENTRE = 5.0f;
    private static final float NEIGHBOUR = -1.0f;

    private static final Kernel KERNEL = new Kernel(3, 3, new float[] {
            0.0f,      NEIGHBOUR, 0.0f,
            NEIGHBOUR, CENTRE,    NEIGHBOUR,
            0.0f,      NEIGHBOUR, 0.0f
    });

    @Override
    public SourceImage apply(SourceImage source) {
        BufferedImage original = source.image();
        if (original.getWidth() < 3 || original.getHeight() < 3) {
            return source;
        }
        BufferedImage output = new BufferedImage(
                original.getWidth(), original.getHeight(), imageTypeOf(original));
        new ConvolveOp(KERNEL, ConvolveOp.EDGE_NO_OP, null).filter(original, output);
        return source.withImage(output);
    }

    /**
     * Convolution needs a type it can address directly; an indexed or custom
     * image is promoted to grayscale rather than refused.
     */
    private static int imageTypeOf(BufferedImage image) {
        return switch (image.getType()) {
            case BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_INT_RGB,
                 BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_3BYTE_BGR -> image.getType();
            default -> BufferedImage.TYPE_BYTE_GRAY;
        };
    }
}
