package com.invoiceocr.extraction.normalization;

import java.util.List;
import java.util.Objects;

/** Applies several normalisers in order; the value flows through each stage. */
public final class NormalizerChain implements ValueNormalizer {

    private final List<ValueNormalizer> stages;

    public NormalizerChain(List<ValueNormalizer> stages) {
        this.stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }

    public static NormalizerChain of(ValueNormalizer... stages) {
        return new NormalizerChain(List.of(stages));
    }

    @Override
    public String normalize(String value) {
        String current = value;
        for (ValueNormalizer stage : stages) {
            current = stage.normalize(current);
        }
        return current;
    }
}
