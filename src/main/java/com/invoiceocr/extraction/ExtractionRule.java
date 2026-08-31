package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.extraction.normalization.ValueNormalizer;
import com.invoiceocr.extraction.validation.ValueCheck;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Binds one field to the strategy that finds it, the normaliser that cleans it
 * and the check that decides whether the result can be true.
 *
 * <p>A rule is data. Adding a field to the application means adding a rule to a
 * provider - no existing class changes.</p>
 *
 * <p>The check is the part that turned the ladder from a fallback chain into a
 * search. Without one, the first rung to produce anything wins by default. With
 * one, a rung whose answer fails a control digit steps aside and the next rung
 * gets its turn - which is how the supplier's fiscal code wins over the buyer's
 * on a page that prints them in an order the regions cannot tell apart.</p>
 */
public record ExtractionRule(FieldDefinition field, FieldExtractor extractor,
                             ValueNormalizer normalizer, ValueCheck check) {

    public ExtractionRule {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(normalizer, "normalizer");
        Objects.requireNonNull(check, "check");
    }

    public static Builder forField(FieldDefinition field) {
        return new Builder(field);
    }

    /** Fluent assembly, so a rule set reads as a specification. */
    public static final class Builder {

        private final FieldDefinition field;
        private final List<FieldExtractor> extractors = new ArrayList<>();
        private ValueNormalizer normalizer = ValueNormalizer.identity();
        private ValueCheck check;

        private Builder(FieldDefinition field) {
            this.field = Objects.requireNonNull(field, "field");
        }

        /** Regular expressions tried in the given order; the first hit wins. */
        public Builder matching(String... regexes) {
            Arrays.stream(regexes).map(RegexFieldExtractor::of).forEach(extractors::add);
            return this;
        }

        /** Any other extraction strategy, appended after the ones already registered. */
        public Builder using(FieldExtractor extractor) {
            extractors.add(Objects.requireNonNull(extractor, "extractor"));
            return this;
        }

        public Builder normalizedBy(ValueNormalizer valueNormalizer) {
            this.normalizer = Objects.requireNonNull(valueNormalizer, "valueNormalizer");
            return this;
        }

        /** The test a candidate has to survive. Several may be given; all apply. */
        public Builder checkedBy(ValueCheck valueCheck) {
            Objects.requireNonNull(valueCheck, "valueCheck");
            this.check = this.check == null ? valueCheck : this.check.and(valueCheck);
            return this;
        }

        public ExtractionRule build() {
            if (extractors.isEmpty()) {
                throw new IllegalStateException("Rule for field " + field.key() + " has no extractor");
            }
            FieldExtractor extractor = extractors.size() == 1
                    ? extractors.get(0)
                    : new FirstMatchFieldExtractor(extractors);
            return new ExtractionRule(field, extractor, normalizer,
                    check == null ? ValueCheck.none() : check);
        }
    }
}
