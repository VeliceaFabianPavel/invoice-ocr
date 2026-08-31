package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lets another extractor run only if the page looks like the right kind of
 * document.
 *
 * <p>The guessing strategies exist so that an awkward layout still yields a
 * value. They must not turn a page that is not an invoice at all — a blank
 * scan, the back of a form, OCR noise — into a confident wrong answer. Gating
 * "the largest amount is the total" on the page mentioning a total somewhere
 * keeps the guess where it belongs: on invoices whose totals block simply did
 * not match a label.</p>
 */
public final class ContextGatedExtractor implements FieldExtractor {

    private final FieldExtractor delegate;
    private final Pattern required;

    public ContextGatedExtractor(FieldExtractor delegate, String requiredSomewhereOnPage) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.required = Pattern.compile(
                Objects.requireNonNull(requiredSomewhereOnPage, "requiredSomewhereOnPage"),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    @Override
    public Optional<Extraction> extract(SearchText text, TextRegion region) {
        if (!text.matcher(required, text.whole()).find()) {
            return Optional.empty();
        }
        return delegate.extract(text, region).map(found -> found.via("gated"));
    }
}
