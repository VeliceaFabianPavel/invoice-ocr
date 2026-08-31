package com.invoiceocr.extraction.validation;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.Refinements;
import com.invoiceocr.extraction.text.Amounts;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Makes the totals block agree with itself.
 *
 * <p>Reading three amounts off a page gives three independent chances to be
 * wrong. Reading them and then requiring {@code net + VAT = total} makes each
 * one evidence for the other two, and this is the pass that applies it:</p>
 *
 * <ol>
 *   <li><b>All three found and they add up.</b> Nothing to fix — but a great
 *       deal to say. Each figure is now corroborated by the other two, so all
 *       three are promoted to {@code VERIFIED} whatever strategy found them.</li>
 *   <li><b>Two found.</b> The third follows exactly, and is filled in as
 *       {@code DERIVED}. This is where most newly-populated amounts come from:
 *       an invoice that labels only the net line and the amount due yields a VAT
 *       figure that was never printed on it.</li>
 *   <li><b>All three found and they do not add up.</b> One was misread. Each is
 *       dropped in turn and recomputed from the other two; the reading whose
 *       survivors imply a VAT rate that actually exists wins, and the odd one
 *       out is replaced.</li>
 *   <li><b>Only the total, plus a rate printed on the page.</b> A "TVA 19%"
 *       beside a gross figure is enough to recover the other two.</li>
 *   <li><b>Nothing reconciles.</b> The figures are kept exactly as read and
 *       marked for review, because a total the user can check beats no total.</li>
 * </ol>
 *
 * <p>Nothing here invents a figure out of nothing: every value it writes is
 * implied by two values that were actually printed.</p>
 */
public final class ArithmeticRefinement implements InvoiceRefinement {

    private static final String COMPUTED = "arithmetic";
    private static final String CONFIRMED = "arithmetic-confirmed";
    private static final String CORRECTED = "arithmetic-corrected";
    private static final String DISPUTED = "arithmetic-disputed";

    /** What a figure is worth once the other two contradict it. */
    private static final double DISPUTED_CONFIDENCE = 0.30;

    @Override
    public InvoiceData refine(InvoiceData data, SearchText text) {
        Optional<BigDecimal> net = numberOf(data, InvoiceFields.NET_AMOUNT);
        Optional<BigDecimal> vat = numberOf(data, InvoiceFields.VAT_AMOUNT);
        Optional<BigDecimal> total = numberOf(data, InvoiceFields.TOTAL_AMOUNT);
        Map<FieldDefinition, ExtractedField> updates = new LinkedHashMap<>();

        boolean haveAll = net.isPresent() && vat.isPresent() && total.isPresent();
        if (haveAll && reconcileThree(net.get(), vat.get(), total.get(), updates)) {
            return apply(data, updates);
        }

        // Nothing about the three of them worked. Before solving for the missing
        // one, drop any figure that cannot be a component of the total at all.
        Optional<BigDecimal> usableNet = plausiblePart(net, total);
        Optional<BigDecimal> usableVat = plausiblePart(vat, total);
        int known = (usableNet.isPresent() ? 1 : 0)
                + (usableVat.isPresent() ? 1 : 0)
                + (total.isPresent() ? 1 : 0);

        if (known == 3) {
            dispute(updates, InvoiceFields.NET_AMOUNT, net.get());
            dispute(updates, InvoiceFields.VAT_AMOUNT, vat.get());
            dispute(updates, InvoiceFields.TOTAL_AMOUNT, total.get());
        } else if (known == 2) {
            completeThird(usableNet, usableVat, total, updates);
        } else if (known == 1 && total.isPresent()) {
            printedRate(text).ifPresent(rate -> splitGross(total.get(), rate, updates));
        }
        return apply(data, updates);
    }

    // ------------------------------------------------------- the three cases

    /** @return true when the three figures could be made to agree */
    private static boolean reconcileThree(BigDecimal net, BigDecimal vat, BigDecimal total,
                                          Map<FieldDefinition, ExtractedField> updates) {
        if (InvoiceArithmetic.addsUp(net, vat, total)) {
            confirm(updates, InvoiceFields.NET_AMOUNT, net);
            confirm(updates, InvoiceFields.VAT_AMOUNT, vat);
            confirm(updates, InvoiceFields.TOTAL_AMOUNT, total);
            return true;
        }
        // One of the three was misread. Trust the pair that implies a real rate.
        if (InvoiceArithmetic.plausiblePair(net, vat)) {
            correct(updates, InvoiceFields.TOTAL_AMOUNT, net.add(vat));
            return true;
        }
        BigDecimal impliedNet = total.subtract(vat);
        if (InvoiceArithmetic.plausiblePair(impliedNet, vat)) {
            correct(updates, InvoiceFields.NET_AMOUNT, impliedNet);
            return true;
        }
        BigDecimal impliedVat = total.subtract(net);
        if (InvoiceArithmetic.plausiblePair(net, impliedVat)) {
            correct(updates, InvoiceFields.VAT_AMOUNT, impliedVat);
            return true;
        }
        return false;
    }

    private static void completeThird(Optional<BigDecimal> net, Optional<BigDecimal> vat,
                                      Optional<BigDecimal> total,
                                      Map<FieldDefinition, ExtractedField> updates) {
        if (net.isPresent() && vat.isPresent()) {
            derive(updates, InvoiceFields.TOTAL_AMOUNT, net.get().add(vat.get()));
        } else if (total.isPresent() && vat.isPresent()) {
            derive(updates, InvoiceFields.NET_AMOUNT, total.get().subtract(vat.get()));
        } else if (total.isPresent() && net.isPresent()) {
            derive(updates, InvoiceFields.VAT_AMOUNT, total.get().subtract(net.get()));
        }
    }

    private static void splitGross(BigDecimal total, BigDecimal rate,
                                   Map<FieldDefinition, ExtractedField> updates) {
        BigDecimal net = InvoiceArithmetic.netOf(total, rate);
        derive(updates, InvoiceFields.NET_AMOUNT, net);
        derive(updates, InvoiceFields.VAT_AMOUNT, total.subtract(net));
    }

    // ------------------------------------------------------------- the rate

    /**
     * A VAT rate printed on the page, as in "TVA 19%".
     *
     * <p>Only rates that are actually charged are accepted, so an early-payment
     * discount of "10%" further up the page cannot be mistaken for one.</p>
     */
    private static Optional<BigDecimal> printedRate(SearchText text) {
        return ValuePatterns.vatRate().firstIn(text, text.whole())
                .map(found -> new BigDecimal(found.value().replaceAll("[^0-9]", "")))
                .filter(InvoiceArithmetic.VAT_RATES::contains);
    }

    // --------------------------------------------------------------- writing

    private static void confirm(Map<FieldDefinition, ExtractedField> updates,
                                FieldDefinition field, BigDecimal value) {
        put(updates, field, value, FieldConfidence.VERIFIED, CONFIRMED);
    }

    private static void derive(Map<FieldDefinition, ExtractedField> updates,
                               FieldDefinition field, BigDecimal value) {
        put(updates, field, value, FieldConfidence.DERIVED, COMPUTED);
    }

    private static void correct(Map<FieldDefinition, ExtractedField> updates,
                                FieldDefinition field, BigDecimal value) {
        put(updates, field, value, FieldConfidence.DERIVED, CORRECTED);
    }

    private static void dispute(Map<FieldDefinition, ExtractedField> updates,
                                FieldDefinition field, BigDecimal value) {
        put(updates, field, value, DISPUTED_CONFIDENCE, DISPUTED);
    }

    private static void put(Map<FieldDefinition, ExtractedField> updates, FieldDefinition field,
                            BigDecimal value, double confidence, String strategy) {
        updates.put(field, ExtractedField.of(field,
                InvoiceArithmetic.scale(value).toPlainString(), confidence, strategy));
    }

    private static InvoiceData apply(InvoiceData data, Map<FieldDefinition, ExtractedField> updates) {
        return Refinements.replaceAll(data, List.copyOf(updates.values()));
    }

    private static Optional<BigDecimal> numberOf(InvoiceData data, FieldDefinition field) {
        return data.valueOf(field).flatMap(Amounts::toNumber);
    }

    /**
     * Discards a component that cannot be one.
     *
     * <p>Neither the net amount nor the VAT can be as large as the total they
     * make up. When one of them is, it is not a component that was misread — it
     * is a different number that a label picked up, most often the amount due
     * caught by a "TVA 19%" heading a couple of lines above it. Dropping it here
     * lets the rest of this pass work from the one figure that is trustworthy,
     * rather than solving an equation with a wrong term in it.</p>
     */
    private static Optional<BigDecimal> plausiblePart(Optional<BigDecimal> part,
                                                      Optional<BigDecimal> total) {
        if (part.isEmpty() || total.isEmpty()) {
            return part;
        }
        boolean impossible = part.get().signum() < 0
                || part.get().subtract(total.get()).compareTo(InvoiceArithmetic.TOLERANCE.negate()) >= 0;
        return impossible ? Optional.empty() : part;
    }
}
