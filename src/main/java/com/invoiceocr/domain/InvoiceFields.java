package com.invoiceocr.domain;

import java.util.List;
import java.util.Optional;

/**
 * Catalog of the invoice fields this application knows about.
 *
 * <p>Purely declarative: it says <em>what</em> can be extracted, never <em>how</em>.
 * The "how" lives in {@code com.invoiceocr.extraction}.</p>
 *
 * <p>The display orders are spaced rather than consecutive so a field can be
 * slipped between two existing ones without renumbering the catalog — which is
 * how the six fields of 1.1 became the twelve of 1.2 without touching a single
 * order already in use.</p>
 */
public final class InvoiceFields {

    public static final FieldDefinition SUPPLIER = new FieldDefinition("supplier", "field.supplier", 10);
    public static final FieldDefinition BUYER = new FieldDefinition("buyer", "field.buyer", 15);
    public static final FieldDefinition INVOICE_NUMBER = new FieldDefinition("invoiceNumber", "field.invoiceNumber", 20);
    public static final FieldDefinition ISSUE_DATE = new FieldDefinition("issueDate", "field.issueDate", 30);
    public static final FieldDefinition DUE_DATE = new FieldDefinition("dueDate", "field.dueDate", 35);
    public static final FieldDefinition FISCAL_CODE = new FieldDefinition("fiscalCode", "field.fiscalCode", 40);
    public static final FieldDefinition REGISTRATION_NUMBER =
            new FieldDefinition("registrationNumber", "field.registrationNumber", 42);
    public static final FieldDefinition IBAN = new FieldDefinition("iban", "field.iban", 44);
    public static final FieldDefinition NET_AMOUNT = new FieldDefinition("netAmount", "field.netAmount", 45);
    public static final FieldDefinition VAT_AMOUNT = new FieldDefinition("vatAmount", "field.vatAmount", 50);
    public static final FieldDefinition TOTAL_AMOUNT = new FieldDefinition("totalAmount", "field.totalAmount", 60);
    public static final FieldDefinition CURRENCY = new FieldDefinition("currency", "field.currency", 65);

    /** Immutable, display-ordered view of the built-in catalog. */
    public static final List<FieldDefinition> ALL = List.of(
            SUPPLIER, BUYER, INVOICE_NUMBER, ISSUE_DATE, DUE_DATE, FISCAL_CODE,
            REGISTRATION_NUMBER, IBAN, NET_AMOUNT, VAT_AMOUNT, TOTAL_AMOUNT, CURRENCY);

    /**
     * The three amounts tied together by {@code net + VAT = total}.
     *
     * <p>Named here rather than inside the reconciler so the identity is stated
     * once, next to the fields it relates.</p>
     */
    public static final List<FieldDefinition> MONEY = List.of(NET_AMOUNT, VAT_AMOUNT, TOTAL_AMOUNT);

    /** Looks a field up by its technical key, for configuration and exports. */
    public static Optional<FieldDefinition> byKey(String key) {
        return ALL.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    private InvoiceFields() {
        throw new AssertionError("No instances");
    }
}
