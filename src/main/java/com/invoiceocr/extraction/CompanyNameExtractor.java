package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Returns the first line that reads like a company name.
 *
 * <p>Many invoices never label the supplier at all: the name is simply the
 * letterhead at the top of the page. A Romanian company name is still
 * recognisable on its own, because it carries its legal form — {@code SRL},
 * {@code SA}, {@code PFA} — so a line holding one of those, inside the
 * supplier's part of the page, is the supplier.</p>
 *
 * <p>A leading label is stripped, so both of these yield the same answer:</p>
 *
 * <pre>
 *   FURNIZOR: SC ALFA CONSTRUCT SRL
 *   SC ALFA CONSTRUCT SRL
 * </pre>
 */
public final class CompanyNameExtractor implements FieldExtractor {

    private static final Pattern LEADING_LABEL = Pattern.compile(
            "^\\s*(?:Furnizor(?:ul)?|Vanzator(?:ul)?|Emitent(?:ul)?|Prestator(?:ul)?"
                    + "|Societate(?:a)?|Denumire(?:a)?|Cumparator(?:ul)?|Client(?:ul)?|Beneficiar(?:ul)?)"
                    + "\\s*[:.\\-]?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lines that carry a legal form but are plainly not the name itself.
     *
     * <p>An address is the case that matters: "Str. Fabricii 2, langa SC VECHI
     * SRL" carries a legal form and would otherwise be read as the supplier.
     * Note that {@code str\.} cannot be followed by {@code \b} — a dot and a
     * space are both non-word characters, so there is no boundary between them
     * and the alternative would never match.</p>
     */
    private static final Pattern NOT_A_NAME = Pattern.compile(
            "\\bstr\\.|\\b(?:factura|chitanta|aviz|contract|banca|iban|cont|adresa|strada"
                    + "|bd\\b|bulevardul|sos\\b|soseaua|tel|email|www)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final int MINIMUM_LENGTH = 4;

    @Override
    public Optional<String> extract(SearchText text, TextRegion region) {
        String folded = text.folded();
        int at = region.start();

        while (at < region.end()) {
            int end = Math.min(text.endOfLine(at), region.end());
            String foldedLine = folded.substring(at, end);

            if (ValuePatterns.COMPANY_LINE.matcher(foldedLine).find()
                    && !NOT_A_NAME.matcher(foldedLine).find()) {
                String candidate = stripLabel(text.slice(at, end), foldedLine);
                if (candidate.length() >= MINIMUM_LENGTH) {
                    return Optional.of(candidate);
                }
            }
            at = end + 1;
        }
        return Optional.empty();
    }

    /** Removes a label prefix, measuring on the folded line so accents cannot hide it. */
    private static String stripLabel(String original, String foldedLine) {
        var matcher = LEADING_LABEL.matcher(foldedLine);
        String result = matcher.lookingAt() ? original.substring(matcher.end()) : original;
        return result.trim();
    }
}
