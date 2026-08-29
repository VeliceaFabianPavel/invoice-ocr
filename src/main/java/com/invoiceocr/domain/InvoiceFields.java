package com.invoiceocr.domain;

import java.util.List;

/**
 * Catalog of the invoice fields this application knows about.
 *
 * <p>Purely declarative: it says <em>what</em> can be extracted, never <em>how</em>.
 * The "how" lives in {@code com.invoiceocr.extraction}.</p>
 */
public final class InvoiceFields {

    public static final FieldDefinition SUPPLIER = new FieldDefinition("supplier", "field.supplier", 10);
    public static final FieldDefinition INVOICE_NUMBER = new FieldDefinition("invoiceNumber", "field.invoiceNumber", 20);
    public static final FieldDefinition ISSUE_DATE = new FieldDefinition("issueDate", "field.issueDate", 30);
    public static final FieldDefinition FISCAL_CODE = new FieldDefinition("fiscalCode", "field.fiscalCode", 40);
    public static final FieldDefinition VAT_AMOUNT = new FieldDefinition("vatAmount", "field.vatAmount", 50);
    public static final FieldDefinition TOTAL_AMOUNT = new FieldDefinition("totalAmount", "field.totalAmount", 60);

    /** Immutable, display-ordered view of the built-in catalog. */
    public static final List<FieldDefinition> ALL = List.of(
            SUPPLIER, INVOICE_NUMBER, ISSUE_DATE, FISCAL_CODE, VAT_AMOUNT, TOTAL_AMOUNT);

    private InvoiceFields() {
        throw new AssertionError("No instances");
    }
}
