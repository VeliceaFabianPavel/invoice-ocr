package com.invoiceocr.extraction.normalization;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tidies an invoice number.
 *
 * <p>A capture that starts at the series often drags the connecting word along
 * with it — {@code ALF nr. 00420} — because that word sits between the two
 * halves of the value rather than before it. Removing those words here keeps
 * the patterns simple and gives {@code ALF 00420}.</p>
 */
public final class DocumentNumberNormalizer implements ValueNormalizer {

    private static final Pattern NOISE_WORDS = Pattern.compile(
            "(?<![\\w])(?:nr|no|num[a]r(?:ul)?|seria|din|factura|fiscala)\\s*[.:]?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s.,:;/\\-]+|[\\s.,:;/\\-]+$");

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = NOISE_WORDS.matcher(value).replaceAll(" ");
        cleaned = EDGE_PUNCTUATION.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.toUpperCase(Locale.ROOT);
    }
}
