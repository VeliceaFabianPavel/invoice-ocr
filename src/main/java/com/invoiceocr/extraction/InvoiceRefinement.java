package com.invoiceocr.extraction;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.extraction.text.SearchText;

/**
 * A second look at the whole invoice, once every field has been read.
 *
 * <p>Rules are deliberately blind to one another: each finds its own field and
 * knows nothing about the rest of the page. That independence is what keeps them
 * simple, and it is also their ceiling — no rule can notice that the VAT it
 * found and the total someone else found do not add up.</p>
 *
 * <p>A refinement runs after the rules and sees everything at once. It can fill
 * a gap from what is already known, correct a figure that contradicts the
 * others, attach the rows of the goods table, or simply lower its opinion of a
 * value it cannot make sense of. Refinements are applied in order and each sees
 * the previous one's output.</p>
 */
@FunctionalInterface
public interface InvoiceRefinement {

    /**
     * @param data the invoice as it stands
     * @param text the page the data came from, for anything the fields do not carry
     * @return the invoice as it should now stand; the same instance when nothing changed
     */
    InvoiceData refine(InvoiceData data, SearchText text);

    /** The neutral element: returns its input untouched. */
    static InvoiceRefinement none() {
        return (data, text) -> data;
    }
}
