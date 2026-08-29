package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tries several strategies and keeps the first hit.
 *
 * <p>Order expresses confidence, which a single alternation cannot: matching
 * {@code Total de plata} before a bare {@code Total} is a priority decision,
 * whereas one regex with {@code |} would simply take whichever label appears
 * earliest on the page.</p>
 *
 * <p>It is also how a field degrades gracefully. The chain runs from the most
 * specific labelled strategy down to a shape-based guess, so a layout that
 * defeats every label still produces an answer rather than {@code N/A}.</p>
 */
public final class FirstMatchFieldExtractor implements FieldExtractor {

    private final List<FieldExtractor> candidates;

    public FirstMatchFieldExtractor(List<FieldExtractor> candidates) {
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    public static FirstMatchFieldExtractor of(FieldExtractor... candidates) {
        return new FirstMatchFieldExtractor(List.of(candidates));
    }

    /** Convenience factory: compiles each pattern into a {@link RegexFieldExtractor}. */
    public static FirstMatchFieldExtractor ofPatterns(String... regexes) {
        return new FirstMatchFieldExtractor(
                Arrays.stream(regexes).map(RegexFieldExtractor::of).map(FieldExtractor.class::cast).toList());
    }

    @Override
    public Optional<String> extract(SearchText text, TextRegion region) {
        for (FieldExtractor candidate : candidates) {
            Optional<String> value = candidate.extract(text, region);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
}
