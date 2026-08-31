package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.ArrayList;
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
 *
 * <p>Because the order already means "most trustworthy first", each further rung
 * discounts what it finds. A value that only the fourth rung could produce is
 * reported as such, which is exactly what a later pass needs in order to
 * overrule it.</p>
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
    public Optional<Extraction> extract(SearchText text, TextRegion region) {
        double discount = 1.0;
        for (FieldExtractor candidate : candidates) {
            Optional<Extraction> found = candidate.extract(text, region);
            if (found.isPresent()) {
                return Optional.of(found.get().scaledBy(discount));
            }
            discount *= FieldConfidence.LADDER_DECAY;
        }
        return Optional.empty();
    }

    /**
     * Every answer the ladder can give, best rung first.
     *
     * <p>Used by the checks that can tell a good value from a bad one — a fiscal
     * code with a control digit, an amount that has to add up. They need the
     * runner-up when the leader fails, and a plain first-hit search cannot give
     * them one.</p>
     */
    @Override
    public List<Extraction> alternatives(SearchText text, TextRegion region) {
        List<Extraction> found = new ArrayList<>();
        double discount = 1.0;
        for (FieldExtractor candidate : candidates) {
            Optional<Extraction> hit = candidate.extract(text, region);
            double applied = discount;
            hit.map(value -> value.scaledBy(applied)).ifPresent(found::add);
            discount *= FieldConfidence.LADDER_DECAY;
        }
        return found;
    }
}
