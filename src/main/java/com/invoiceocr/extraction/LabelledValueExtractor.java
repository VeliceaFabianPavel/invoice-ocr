package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePattern;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a label, then looks for a value shortly after it.
 *
 * <p>This is the strategy that fixes most missing fields. The old rules required
 * the value to sit on the same line as its label, which is only one of the ways
 * an invoice is laid out. Just as common are:</p>
 *
 * <pre>
 *   Nr. factura     Data emiterii        Furnizor:
 *   GML-7781        02.02.2024           SC ALFA CONSTRUCT SRL
 * </pre>
 *
 * <p>Allowing the value to appear within the next few lines catches both,
 * while the line budget keeps the search from wandering into an unrelated part
 * of the page. Each label occurrence is tried in turn, so a heading that
 * happens to have nothing after it does not stop the search.</p>
 */
public final class LabelledValueExtractor implements FieldExtractor {

    private final Pattern label;
    private final ValuePattern value;
    private final int lineBudget;

    /**
     * @param label      pattern for the label, matched against folded text
     * @param value      the shape of the value to look for after it
     * @param lineBudget how many further lines may be searched; 0 means the
     *                   rest of the label's own line only
     */
    public LabelledValueExtractor(String label, ValuePattern value, int lineBudget) {
        this.label = Pattern.compile(Objects.requireNonNull(label, "label"),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
        this.value = Objects.requireNonNull(value, "value");
        if (lineBudget < 0) {
            throw new IllegalArgumentException("lineBudget must not be negative");
        }
        this.lineBudget = lineBudget;
    }

    /** Same line only. */
    public static LabelledValueExtractor sameLine(String label, ValuePattern value) {
        return new LabelledValueExtractor(label, value, 0);
    }

    /** The rest of the label's line, plus the next {@code lines} lines. */
    public static LabelledValueExtractor within(String label, ValuePattern value, int lines) {
        return new LabelledValueExtractor(label, value, lines);
    }

    @Override
    public Optional<String> extract(SearchText text, TextRegion region) {
        Matcher matcher = text.matcher(label, region);
        while (matcher.find()) {
            int from = matcher.end();
            int to = Math.min(text.skipLines(from, lineBudget + 1), region.end());
            if (to <= from) {
                continue;
            }
            Optional<String> found = value.firstIn(text, new TextRegion(from, to))
                    .map(ValuePattern.Found::value);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
