package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.regex.Matcher;

/**
 * Works out which part of the page belongs to the supplier.
 *
 * <p>An invoice names two companies, each with a name and a fiscal code printed
 * in exactly the same shape. Nothing in the values themselves says which is
 * which — only their position does. Without this, the first {@code RO…} on the
 * page wins, and on an invoice that prints the buyer first that is the wrong
 * company's code.</p>
 *
 * <p>The rule is simple and survives both orderings: the supplier's block runs
 * from its own marker (or the top of the page, since a letterhead usually is
 * the supplier) to the buyer's marker, or to the end of the page when the buyer
 * is named first.</p>
 */
public final class DocumentRegions {

    /** How far past the supplier marker to keep looking when no buyer marker follows. */
    private static final int TRAILING_LINES = 8;

    /** The part of the page that describes the supplier. */
    public static TextRegion supplier(SearchText text) {
        TextRegion whole = text.whole();
        Matcher supplierMarker = text.matcher(ValuePatterns.SUPPLIER_MARKER, whole);
        Matcher buyerMarker = text.matcher(ValuePatterns.BUYER_MARKER, whole);

        boolean hasSupplier = supplierMarker.find();
        boolean hasBuyer = buyerMarker.find();

        if (!hasSupplier && !hasBuyer) {
            return whole;                       // one company on the page, or none labelled
        }
        if (!hasSupplier) {
            // Only the buyer is marked: the supplier is whatever comes before it.
            return whole.to(text.startOfLine(buyerMarker.start()));
        }

        int start = text.startOfLine(supplierMarker.start());
        if (!hasBuyer) {
            return whole.from(start);
        }
        if (buyerMarker.start() > supplierMarker.start()) {
            return new TextRegion(start, text.startOfLine(buyerMarker.start()));
        }
        // Buyer printed first: the supplier's block is everything from its marker on.
        return whole.from(start);
    }

    /**
     * A tighter view of the supplier's block: the marker plus a few lines. Used
     * where a stray match further down the page would be worse than no match.
     */
    public static TextRegion supplierHeader(SearchText text) {
        TextRegion supplier = supplier(text);
        int limited = text.skipLines(supplier.start(), TRAILING_LINES);
        return supplier.to(limited);
    }

    private DocumentRegions() {
        throw new AssertionError("No instances");
    }
}
