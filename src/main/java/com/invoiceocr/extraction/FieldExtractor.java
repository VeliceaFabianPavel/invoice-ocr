package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.Optional;

/**
 * Locates the raw text of a single field on a page.
 *
 * <p>An extractor knows nothing about which field it serves, how the value
 * should be formatted, or where the text came from, which is what keeps every
 * strategy interchangeable: a label followed by a value, a label with the value
 * on the line below, a value recognised by its own shape anywhere on the page,
 * or the largest amount printed.</p>
 *
 * <p>Searching is bounded by a {@link TextRegion} so a strategy can be confined
 * to part of the page — the supplier's block rather than the buyer's.</p>
 *
 * <p>The result is an {@link Extraction} rather than a string, so the answer
 * arrives with the evidence behind it. Nothing downstream has to guess how good
 * a value is from the value alone.</p>
 */
@FunctionalInterface
public interface FieldExtractor {

    /** @return the raw capture with its rating, or empty when this extractor finds nothing */
    Optional<Extraction> extract(SearchText text, TextRegion region);

    /** Searches the whole page. */
    default Optional<Extraction> extract(SearchText text) {
        return extract(text, text.whole());
    }

    /** Just the text of what was found, for callers that do not care how. */
    default Optional<String> extractValue(SearchText text) {
        return extract(text).map(Extraction::value);
    }

    /**
     * Every answer this extractor can offer, best first.
     *
     * <p>A plain extractor has exactly one. A ladder has as many as it has rungs,
     * and that is the point: a check that can tell a good value from a bad one
     * needs the runner-up when the leader fails its checksum, and a first-hit
     * search cannot give it one.</p>
     */
    default java.util.List<Extraction> alternatives(SearchText text, TextRegion region) {
        return extract(text, region).map(java.util.List::of).orElseGet(java.util.List::of);
    }
}
