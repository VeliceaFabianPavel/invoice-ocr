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
 */
@FunctionalInterface
public interface FieldExtractor {

    /** @return the raw capture, or empty when this extractor finds nothing */
    Optional<String> extract(SearchText text, TextRegion region);

    /** Searches the whole page. */
    default Optional<String> extract(SearchText text) {
        return extract(text, text.whole());
    }
}
