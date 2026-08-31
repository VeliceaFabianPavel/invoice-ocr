package com.invoiceocr.recognition;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.image.AdaptiveThresholdPreprocessor;
import com.invoiceocr.image.CompositeImagePreprocessor;
import com.invoiceocr.image.ContrastStretchPreprocessor;
import com.invoiceocr.image.DeskewPreprocessor;
import com.invoiceocr.image.GrayscalePreprocessor;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.image.SharpenPreprocessor;
import com.invoiceocr.image.UpscalePreprocessor;
import java.util.List;
import java.util.Objects;

/**
 * The ordered set of attempts to make at one page.
 *
 * <p>Order is the whole design. The first pass is the cheap one that has always
 * been run, so a clean scan costs exactly what it used to and stops there. Each
 * later pass exists because it rescues a kind of page the ones before it cannot,
 * and it is placed by how often that kind of page turns up:</p>
 *
 * <ol>
 *   <li><b>plain</b> — upscale and grey. The pipeline of 1.1, and enough for a
 *       flatbed scan of a laser-printed invoice.</li>
 *   <li><b>straightened</b> — deskew first, then stretch the contrast. This is
 *       the pass that rescues photographs: a page held at an angle under a desk
 *       lamp.</li>
 *   <li><b>binarised</b> — the same, finished with an adaptive threshold and
 *       read as a single column. For unevenly lit pages, where the shadow is
 *       what defeated the earlier passes rather than the tilt.</li>
 *   <li><b>sharpened</b> — sharpened and read as one block of text. The last
 *       resort, and the one that most often recovers a faint receipt whose
 *       totals block sits in a table the layout analysis broke apart.</li>
 * </ol>
 *
 * <p>Passes after the first are only paid for when the ones before them left
 * something unread or unsure, so the common case costs nothing.</p>
 */
public final class RecognitionPlan {

    /** Tesseract PSM 4: a single column of text of varying sizes. */
    private static final int SINGLE_COLUMN = 4;

    /** Tesseract PSM 6: one uniform block of text. */
    private static final int SINGLE_BLOCK = 6;

    private final List<RecognitionPass> passes;

    public RecognitionPlan(List<RecognitionPass> passes) {
        this.passes = List.copyOf(Objects.requireNonNull(passes, "passes"));
        if (this.passes.isEmpty()) {
            throw new IllegalArgumentException("A plan needs at least one pass");
        }
    }

    /** The built-in ladder, trimmed to the number of passes the settings allow. */
    public static RecognitionPlan forSettings(OcrSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (!settings.preprocessingEnabled()) {
            return new RecognitionPlan(List.of(
                    RecognitionPass.of("as-is", ImagePreprocessor.identity())));
        }
        int width = settings.minimumWidth();
        List<RecognitionPass> all = List.of(
                RecognitionPass.of("plain", CompositeImagePreprocessor.of(
                        new UpscalePreprocessor(width),
                        new GrayscalePreprocessor())),
                RecognitionPass.of("straightened", CompositeImagePreprocessor.of(
                        new DeskewPreprocessor(),
                        new UpscalePreprocessor(width),
                        new ContrastStretchPreprocessor())),
                RecognitionPass.of("binarised", CompositeImagePreprocessor.of(
                        new DeskewPreprocessor(),
                        new UpscalePreprocessor(width),
                        new ContrastStretchPreprocessor(),
                        new AdaptiveThresholdPreprocessor()), SINGLE_COLUMN),
                RecognitionPass.of("sharpened", CompositeImagePreprocessor.of(
                        new DeskewPreprocessor(),
                        new UpscalePreprocessor(width),
                        new SharpenPreprocessor(),
                        new GrayscalePreprocessor()), SINGLE_BLOCK));

        int limit = Math.max(1, Math.min(settings.maximumPasses(), all.size()));
        return new RecognitionPlan(all.subList(0, limit));
    }

    /** A plan of exactly one pass, which is what 1.1 did and what a test usually wants. */
    public static RecognitionPlan singlePass(ImagePreprocessor preprocessor) {
        return new RecognitionPlan(List.of(RecognitionPass.of("single", preprocessor)));
    }

    public List<RecognitionPass> passes() {
        return passes;
    }

    public int size() {
        return passes.size();
    }
}
