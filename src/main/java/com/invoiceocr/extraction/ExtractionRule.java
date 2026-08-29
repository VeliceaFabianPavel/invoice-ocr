package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.extraction.normalization.ValueNormalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Binds one field to the strategy that finds it and the normaliser that cleans it.
 *
 * <p>A rule is data. Adding a field to the application means adding a rule to a
 * provider - no existing class changes.</p>
 */
public record ExtractionRule(FieldDefinition field, FieldExtractor extractor, ValueNormalizer normalizer) {

    public ExtractionRule {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(normalizer, "normalizer");
    }

    public static Builder forField(FieldDefinition field) {
        return new Builder(field);
    }

    /** Fluent assembly, so a rule set reads as a specification. */
    public static final class Builder {

        private final FieldDefinition field;
        private final List<FieldExtractor> extractors = new ArrayList<>();
        private ValueNormalizer normalizer = ValueNormalizer.identity();

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

        public ExtractionRule build() {
            if (extractors.isEmpty()) {
                throw new IllegalStateException("Rule for field " + field.key() + " has no extractor");
            }
            FieldExtractor extractor = extractors.size() == 1
                    ? extractors.get(0)
                    : new FirstMatchFieldExtractor(extractors);
            return new ExtractionRule(field, extractor, normalizer);
        }
    }
}
