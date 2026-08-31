package com.invoiceocr.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.domain.RecognizedText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonInvoiceReportFormatter")
class JsonInvoiceReportFormatterTest {

    private final InvoiceReportFormatter formatter = new JsonInvoiceReportFormatter();

    @Test
    @DisplayName("nests the fields under a key of their own, with null for the missing ones")
    void emitsNestedJsonObject() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL"),
                        ExtractedField.missing(InvoiceFields.TOTAL_AMOUNT)));

        String json = formatter.format(data);

        assertTrue(json.contains("\"fields\": {"), json);
        assertTrue(json.contains("\"supplier\": \"ACME SRL\""), json);
        assertTrue(json.contains("\"totalAmount\": null"), json);
    }

    @Test
    @DisplayName("reports how much each value that was found is worth")
    void carriesConfidence() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL",
                                FieldConfidence.LABELLED, "labelled"),
                        ExtractedField.missing(InvoiceFields.TOTAL_AMOUNT)));

        String json = formatter.format(data);

        assertTrue(json.contains("\"confidence\": {"), json);
        assertTrue(json.contains("\"supplier\": 0.90"), json);
        assertTrue(!json.contains("\"totalAmount\": 0.00"),
                "a missing field has no confidence to report");
    }

    @Test
    @DisplayName("carries the rows of the goods table")
    void carriesLineItems() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")),
                List.of(LineItem.of("Ciment Portland", "10", "32.00", "320.00")));

        String json = formatter.format(data);

        assertTrue(json.contains("\"description\": \"Ciment Portland\""), json);
        assertTrue(json.contains("\"quantity\": \"10\""), json);
        assertTrue(json.contains("\"unitPrice\": \"32.00\""), json);
        assertTrue(json.contains("\"value\": \"320.00\""), json);
    }

    @Test
    @DisplayName("an absent column is null rather than an empty string")
    void nullsAnAbsentColumn() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(ExtractedField.missing(InvoiceFields.SUPPLIER)),
                List.of(LineItem.of("Manopera", null, null, "1100.00")));

        String json = formatter.format(data);

        assertTrue(json.contains("\"quantity\": null"), json);
        assertTrue(json.contains("\"unitPrice\": null"), json);
    }

    @Test
    @DisplayName("an invoice with no table carries an empty array, not an absent key")
    void emitsAnEmptyTable() {
        InvoiceData data = InvoiceData.of(RecognizedText.EMPTY,
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")));

        assertTrue(formatter.format(data).contains("\"lineItems\": []"));
    }

    @Test
    @DisplayName("summarises what was read and what wants checking")
    void summarises() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL"),
                        ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "100.00",
                                FieldConfidence.INFERRED, "shape-largest")));

        String json = formatter.format(data);

        assertTrue(json.contains("\"recognized\": 2"), json);
        assertTrue(json.contains("\"fields\": 2"), json);
        assertTrue(json.contains("\"needsReview\": [\"totalAmount\"]"), json);
    }

    @Test
    @DisplayName("escapes quotes inside a value")
    void escapesQuotes() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "SC \"ACME\" SRL")));

        assertTrue(formatter.format(data).contains("\"supplier\": \"SC \\\"ACME\\\" SRL\""));
    }

    @Test
    @DisplayName("numbers are dot-separated whatever locale the JVM is running in")
    void doesNotDependOnTheLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            InvoiceData data = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL",
                            FieldConfidence.LABELLED, "labelled")));

            assertEquals(true, formatter.format(data).contains("0.90"));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
