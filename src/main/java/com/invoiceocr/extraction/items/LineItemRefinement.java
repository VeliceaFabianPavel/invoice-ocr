package com.invoiceocr.extraction.items;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.extraction.DocumentRegions;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.Refinements;
import com.invoiceocr.extraction.text.Amounts;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.validation.InvoiceArithmetic;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Attaches the goods table to the invoice, and uses it as evidence.
 *
 * <p>The rows are worth having on their own — an accountant who wants the line
 * items has until now had to read them off the raw panel by hand. But they are
 * also a second, independent reading of the net amount: the rows have to sum to
 * it. Where a page prints its line items and its totals in different places, one
 * checks the other.</p>
 *
 * <p>The sum is only ever written into the net field when a second figure agrees
 * that it is right. A table with a row missed by OCR sums to too little, and a
 * confidently wrong net amount is worse than an absent one, so the sum has to
 * imply a VAT rate that actually exists before it is believed.</p>
 */
public final class LineItemRefinement implements InvoiceRefinement {

    private static final String FROM_ROWS = "line-item-sum";
    private static final String CONFIRMED_BY_ROWS = "line-items-agree";

    private final LineItemExtractor extractor;

    public LineItemRefinement(LineItemExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    @Override
    public InvoiceData refine(InvoiceData data, SearchText text) {
        TextRegion table = DocumentRegions.items(text);
        if (table.isEmpty()) {
            return data;
        }
        List<LineItem> items = extractor.extract(text, table);
        if (items.isEmpty()) {
            return data;
        }
        return reconcileNet(data.withLineItems(items), sumOf(items));
    }

    /** Confirms the net amount against the rows, or supplies it when it is missing. */
    private static InvoiceData reconcileNet(InvoiceData data, Optional<BigDecimal> sum) {
        if (sum.isEmpty()) {
            return data;
        }
        BigDecimal rows = sum.get();
        Optional<BigDecimal> net = amountOf(data, InvoiceFields.NET_AMOUNT);

        if (net.isPresent()) {
            return net.get().subtract(rows).abs().compareTo(InvoiceArithmetic.TOLERANCE) <= 0
                    ? Refinements.replace(data, ExtractedField.of(InvoiceFields.NET_AMOUNT,
                            InvoiceArithmetic.scale(rows).toPlainString(),
                            FieldConfidence.VERIFIED, CONFIRMED_BY_ROWS))
                    : data;
        }
        return corroborated(data, rows)
                ? Refinements.replace(data, ExtractedField.of(InvoiceFields.NET_AMOUNT,
                        InvoiceArithmetic.scale(rows).toPlainString(),
                        FieldConfidence.DERIVED, FROM_ROWS))
                : data;
    }

    /**
     * True when a figure already on the page agrees that {@code rows} is the net
     * amount: either the VAT sits at a real rate on it, or the total minus it
     * does.
     */
    private static boolean corroborated(InvoiceData data, BigDecimal rows) {
        Optional<BigDecimal> vat = amountOf(data, InvoiceFields.VAT_AMOUNT);
        if (vat.isPresent() && InvoiceArithmetic.plausiblePair(rows, vat.get())) {
            return true;
        }
        Optional<BigDecimal> total = amountOf(data, InvoiceFields.TOTAL_AMOUNT);
        return total.isPresent()
                && InvoiceArithmetic.plausiblePair(rows, total.get().subtract(rows));
    }

    /** The rows added up, or empty when none of them carried a readable figure. */
    static Optional<BigDecimal> sumOf(List<LineItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (LineItem item : items) {
            Optional<BigDecimal> value = Amounts.toNumber(item.lineTotal());
            if (value.isPresent()) {
                sum = sum.add(value.get());
                any = true;
            }
        }
        return any ? Optional.of(InvoiceArithmetic.scale(sum)) : Optional.empty();
    }

    private static Optional<BigDecimal> amountOf(InvoiceData data,
                                                 com.invoiceocr.domain.FieldDefinition field) {
        return data.valueOf(field).flatMap(Amounts::toNumber);
    }

}
