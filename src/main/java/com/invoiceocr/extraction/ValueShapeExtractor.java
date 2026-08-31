package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.extraction.text.Amounts;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePattern;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds a value by its own shape, with no label at all.
 *
 * <p>The last line of defence, and the reason a letterhead invoice still yields
 * a fiscal code: {@code RO2468101} is unmistakable wherever it is printed. For
 * totals the useful selector is {@link Selection#LARGEST} — on an invoice the
 * biggest amount on the page is the amount due far more often than not, which
 * beats reporting nothing.</p>
 *
 * <p>Because these strategies guess, they are registered last in a rule, after
 * every labelled strategy has had its turn, and they are rated accordingly: a
 * shape match is {@code LOOSE}, and picking the largest of several candidates
 * is {@code INFERRED}.</p>
 */
public final class ValueShapeExtractor implements FieldExtractor {

    public enum Selection {
        /** The first one printed. */
        FIRST,
        /** The last one printed, which on a totals block is usually the final figure. */
        LAST,
        /** The numerically largest, for finding a total among line items. */
        LARGEST,
        /** The numerically smallest, for picking the VAT out of a totals block. */
        SMALLEST
    }

    private final ValuePattern value;
    private final Selection selection;

    public ValueShapeExtractor(ValuePattern value, Selection selection) {
        this.value = Objects.requireNonNull(value, "value");
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    public static ValueShapeExtractor first(ValuePattern value) {
        return new ValueShapeExtractor(value, Selection.FIRST);
    }

    public static ValueShapeExtractor last(ValuePattern value) {
        return new ValueShapeExtractor(value, Selection.LAST);
    }

    public static ValueShapeExtractor largest(ValuePattern value) {
        return new ValueShapeExtractor(value, Selection.LARGEST);
    }

    public static ValueShapeExtractor smallest(ValuePattern value) {
        return new ValueShapeExtractor(value, Selection.SMALLEST);
    }

    @Override
    public Optional<Extraction> extract(SearchText text, TextRegion region) {
        List<ValuePattern.Found> all = value.allIn(text, region);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        Optional<ValuePattern.Found> chosen = switch (selection) {
            case FIRST -> Optional.of(all.get(0));
            case LAST -> Optional.of(all.get(all.size() - 1));
            case LARGEST -> all.stream().max(byAmount());
            case SMALLEST -> all.stream().min(byAmount());
        };
        return chosen.map(found -> Extraction.of(found.value(), confidence(), name()));
    }

    /** Unparseable candidates sort to the bottom rather than throwing. */
    private static Comparator<ValuePattern.Found> byAmount() {
        return Comparator.comparing(found -> Amounts.toNumber(found.value())
                .orElse(BigDecimal.valueOf(Long.MIN_VALUE)));
    }

    private double confidence() {
        return switch (selection) {
            case FIRST, LAST -> FieldConfidence.LOOSE;
            case LARGEST, SMALLEST -> FieldConfidence.INFERRED;
        };
    }

    private String name() {
        return "shape-" + selection.name().toLowerCase(java.util.Locale.ROOT);
    }
}
