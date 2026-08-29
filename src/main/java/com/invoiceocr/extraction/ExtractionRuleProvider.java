package com.invoiceocr.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Supplies the rule set a parser should apply.
 *
 * <p>One provider per document dialect (Romanian invoices, German invoices,
 * a specific supplier template). {@link #and(ExtractionRuleProvider)} lets a
 * narrow, supplier-specific set be layered over a generic one.</p>
 */
@FunctionalInterface
public interface ExtractionRuleProvider {

    List<ExtractionRule> rules();

    /** Rules of {@code this} first, then {@code other}; earlier rules win per field. */
    default ExtractionRuleProvider and(ExtractionRuleProvider other) {
        Objects.requireNonNull(other, "other");
        return () -> {
            List<ExtractionRule> combined = new ArrayList<>(rules());
            combined.addAll(other.rules());
            return List.copyOf(combined);
        };
    }
}
