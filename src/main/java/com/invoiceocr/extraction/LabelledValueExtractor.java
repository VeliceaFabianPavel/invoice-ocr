package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePattern;
import java.util.Comparator;
import java.util.List;
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
 *
 * <p>A value found beside its label is rated higher than one found under it,
 * because the second reading depends on a column actually lining up and the
 * first does not.</p>
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
    public Optional<Extraction> extract(SearchText text, TextRegion region) {
        Matcher matcher = text.matcher(label, region);
        while (matcher.find()) {
            int from = matcher.end();
            int to = Math.min(text.skipLines(from, lineBudget + 1), region.end());
            if (to <= from) {
                continue;
            }
            Optional<ValuePattern.Found> found =
                    choose(text, new TextRegion(from, to), matcher.start());
            if (found.isPresent()) {
                return Optional.of(rate(found.get(), from, text));
            }
        }
        return Optional.empty();
    }

    /**
     * Which of the candidates in the window belongs to this label.
     *
     * <p>On the label's own line the answer is the first one, and always has
     * been. Below it, the first one is often the wrong one — a heading row hands
     * three labels to the same line of values:</p>
     *
     * <pre>
     *   Nr. factura     Data emiterii     Termen de plata
     *   GML-7781        02.02.2024        02.03.2024
     * </pre>
     *
     * <p>Both dates sit inside the window that "Termen de plata" opens, and
     * taking the first gives the issue date under a due-date label. What
     * distinguishes them is the column: a table puts a value under its own
     * heading. So candidates below the label are ranked by how near their line
     * is, and then by how far their column sits from the label's — which reads
     * the table the way a person does.</p>
     */
    private Optional<ValuePattern.Found> choose(SearchText text, TextRegion window, int labelStart) {
        List<ValuePattern.Found> candidates = value.allIn(text, window);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int ownLineEnd = text.endOfLine(window.start());
        for (ValuePattern.Found candidate : candidates) {
            if (candidate.start() < ownLineEnd) {
                return Optional.of(candidate);
            }
        }
        if (lineBudget == 0) {
            return Optional.empty();
        }
        int labelColumn = labelStart - text.startOfLine(labelStart);
        return candidates.stream().min(Comparator
                .comparingInt((ValuePattern.Found found) -> text.startOfLine(found.start()))
                .thenComparingInt(found ->
                        Math.abs(found.start() - text.startOfLine(found.start()) - labelColumn)));
    }

    /**
     * A hit on the label's own line keeps the {@code LABELLED} band; one that
     * had to step down to a following line drops to {@code NEARBY}, whatever the
     * budget allowed.
     */
    private Extraction rate(ValuePattern.Found found, int labelEnd, SearchText text) {
        boolean sameLine = found.start() < text.endOfLine(labelEnd);
        return Extraction.of(found.value(),
                sameLine ? FieldConfidence.LABELLED : FieldConfidence.NEARBY,
                sameLine ? "labelled" : "labelled-below");
    }
}
