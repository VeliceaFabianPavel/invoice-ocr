package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.util.List;
import java.util.Objects;

/** Applies a list of preprocessors in declaration order. */
public final class CompositeImagePreprocessor implements ImagePreprocessor {

    private final List<ImagePreprocessor> stages;

    public CompositeImagePreprocessor(List<ImagePreprocessor> stages) {
        this.stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }

    public static CompositeImagePreprocessor of(ImagePreprocessor... stages) {
        return new CompositeImagePreprocessor(List.of(stages));
    }

    @Override
    public SourceImage apply(SourceImage source) {
        SourceImage current = Objects.requireNonNull(source, "source");
        for (ImagePreprocessor stage : stages) {
            current = stage.apply(current);
        }
        return current;
    }
}
