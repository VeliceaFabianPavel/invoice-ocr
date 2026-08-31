package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.regex.Matcher;

/**
 * Works out which part of the page belongs to what.
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
 * is named first. The buyer's block is the mirror image.</p>
 *
 * <p>Two more regions matter for the amounts. The <em>totals</em> block is the
 * foot of the page where the summary figures live, and the <em>items</em> block
 * is the goods table above it. Keeping an amount search inside one of them is
 * what stops a line item being reported as the total, and stops the total being
 * read as a line item.</p>
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
     * The part of the page that describes the buyer.
     *
     * <p>Unlike the supplier, the buyer has no fallback: an unlabelled page has
     * no buyer block, and an empty region is the honest answer. Guessing here
     * would mean reporting the supplier twice.</p>
     */
    public static TextRegion buyer(SearchText text) {
        TextRegion whole = text.whole();
        Matcher buyerMarker = text.matcher(ValuePatterns.BUYER_MARKER, whole);
        if (!buyerMarker.find()) {
            return new TextRegion(whole.end(), whole.end());
        }
        int start = text.startOfLine(buyerMarker.start());

        Matcher supplierMarker = text.matcher(ValuePatterns.SUPPLIER_MARKER, whole);
        while (supplierMarker.find()) {
            if (supplierMarker.start() > buyerMarker.start()) {
                return new TextRegion(start, text.startOfLine(supplierMarker.start()));
            }
        }
        return whole.from(start);
    }

    /**
     * The summary block at the foot of the page.
     *
     * <p>It starts at the first line that introduces a summary figure — the net
     * line, the VAT line or the amount due, whichever comes first — and runs to
     * the end of the page. Every amount that matters is inside it, and none of
     * the line items are.</p>
     */
    public static TextRegion totals(SearchText text) {
        TextRegion whole = text.whole();
        Matcher totalsMarker = text.matcher(ValuePatterns.TOTALS_MARKER, whole);
        if (!totalsMarker.find()) {
            return whole;
        }
        return whole.from(text.startOfLine(totalsMarker.start()));
    }

    /**
     * The goods table: from the line after its column headings down to the
     * totals block.
     *
     * <p>Empty when the page has no recognisable heading row, which is the right
     * answer — an invoice whose table cannot be located is one whose rows should
     * not be guessed at.</p>
     */
    public static TextRegion items(SearchText text) {
        TextRegion whole = text.whole();
        Matcher heading = text.matcher(ValuePatterns.TABLE_HEADING, whole);
        if (!heading.find()) {
            return new TextRegion(whole.end(), whole.end());
        }
        int start = Math.min(text.endOfLine(heading.start()) + 1, whole.end());
        int end = Math.max(start, totals(text).start());
        return new TextRegion(start, end);
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
