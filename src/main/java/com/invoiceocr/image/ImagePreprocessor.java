package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.util.Objects;

/**
 * A pure image-to-image transformation applied before recognition.
 *
 * <p>Implementations are small and composable; {@link CompositeImagePreprocessor}
 * chains them, so the pipeline is data rather than control flow.</p>
 */
@FunctionalInterface
public interface ImagePreprocessor {

    SourceImage apply(SourceImage source);

    default ImagePreprocessor andThen(ImagePreprocessor next) {
        Objects.requireNonNull(next, "next");
        return source -> next.apply(apply(source));
    }

    /** The neutral element: returns its input untouched. */
    static ImagePreprocessor identity() {
        return source -> source;
    }
}
