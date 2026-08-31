package com.invoiceocr.recognition;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.text.SearchText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds one invoice out of several readings of the same page.
 *
 * <p>Two things decide each field, and the second is the one that makes this
 * worth doing:</p>
 *
 * <ul>
 *   <li><b>How the value was found.</b> A figure read beside its own label beats
 *       the same figure guessed from the shape of the page, whichever pass
 *       produced it.</li>
 *   <li><b>How many passes agree.</b> Two independent preparations of an image
 *       arriving at the same string is strong evidence, because the ways OCR goes
 *       wrong are specific to the rendering: a threshold that turns {@code 8} into
 *       {@code B} does not survive being deskewed and sharpened instead. Agreement
 *       is therefore rewarded, and it is what lets a modest reading from two
 *       passes overturn a confident one from a single pass.</li>
 * </ul>
 *
 * <p>The rows of the goods table are taken whole from whichever pass read the
 * most of them, rather than merged row by row. Rows from different renderings of
 * the page cannot be lined up reliably, and half a table from each of two passes
 * would be a table that never existed.</p>
 *
 * <p>Finally the refinements run once more over the merged fields, because the
 * merge can produce a combination no single pass saw — a net amount from one
 * reading beside a total from another — and that combination deserves the same
 * arithmetic as any other.</p>
 */
public final class InvoiceDataMerger {

    /** What one further pass agreeing on a value is worth, added to its confidence. */
    private static final double AGREEMENT_BONUS = 0.15;

    private final List<InvoiceRefinement> refinements;

    public InvoiceDataMerger() {
        this(List.of());
    }

    public InvoiceDataMerger(List<InvoiceRefinement> refinements) {
        this.refinements = List.copyOf(Objects.requireNonNull(refinements, "refinements"));
    }

    /**
     * @param outcomes at least one pass result, in the order the passes ran
     * @return the best invoice the outcomes support
     */
    public InvoiceData merge(List<PassOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("Nothing to merge");
        }
        if (outcomes.size() == 1) {
            return outcomes.get(0).data();
        }

        PassOutcome bestOverall = outcomes.stream()
                .max(Comparator.comparingDouble(PassOutcome::quality))
                .orElseThrow();

        Map<FieldDefinition, ExtractedField> winners = new LinkedHashMap<>();
        for (FieldDefinition field : catalogOf(outcomes)) {
            winners.put(field, bestFor(field, outcomes));
        }

        InvoiceData merged = InvoiceData.of(bestOverall.data().source(),
                List.copyOf(winners.values()), richestTable(outcomes));

        SearchText text = SearchText.of(merged.source());
        for (InvoiceRefinement refinement : refinements) {
            merged = refinement.refine(merged, text);
        }
        return merged;
    }

    /**
     * The best reading of one field across every pass.
     *
     * <p>A missing field is not a vote against a value: an invoice read four ways
     * where one pass found the IBAN and three did not still has that IBAN. Only
     * passes that produced something take part.</p>
     */
    private static ExtractedField bestFor(FieldDefinition field, List<PassOutcome> outcomes) {
        List<ExtractedField> found = new ArrayList<>();
        for (PassOutcome outcome : outcomes) {
            outcome.data().field(field).filter(ExtractedField::isPresent).ifPresent(found::add);
        }
        if (found.isEmpty()) {
            return ExtractedField.missing(field);
        }

        ExtractedField best = found.get(0);
        double bestScore = -1.0;
        for (ExtractedField candidate : found) {
            double score = candidate.confidence() + agreementFor(candidate, found) * AGREEMENT_BONUS;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        long agreeing = agreementFor(best, found);
        return agreeing == 0
                ? best
                : best.ratedAt(FieldConfidence.clamp(best.confidence() + agreeing * AGREEMENT_BONUS),
                        best.strategy() + "+agreed" + (agreeing + 1));
    }

    /** How many other passes read exactly the same value. */
    private static long agreementFor(ExtractedField candidate, List<ExtractedField> found) {
        return found.stream().filter(other -> other.value().equals(candidate.value())).count() - 1;
    }

    /** The table from whichever pass read the most rows; ties go to the earlier pass. */
    private static List<LineItem> richestTable(List<PassOutcome> outcomes) {
        List<LineItem> best = List.of();
        for (PassOutcome outcome : outcomes) {
            if (outcome.data().lineItems().size() > best.size()) {
                best = outcome.data().lineItems();
            }
        }
        return best;
    }

    /** Every field any pass reported, in display order, so nothing is dropped. */
    private static List<FieldDefinition> catalogOf(List<PassOutcome> outcomes) {
        List<FieldDefinition> catalog = new ArrayList<>();
        for (PassOutcome outcome : outcomes) {
            for (ExtractedField field : outcome.data().fields()) {
                if (!catalog.contains(field.definition())) {
                    catalog.add(field.definition());
                }
            }
        }
        catalog.sort(Comparator.naturalOrder());
        return catalog;
    }
}
