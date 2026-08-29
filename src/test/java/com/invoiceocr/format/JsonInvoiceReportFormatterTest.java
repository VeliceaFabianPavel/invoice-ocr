package com.invoiceocr.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonInvoiceReportFormatter")
class JsonInvoiceReportFormatterTest {

    private final InvoiceReportFormatter formatter = new JsonInvoiceReportFormatter();

    @Test
    @DisplayName("emits null for a missing field and quotes the rest")
    void emitsFlatJsonObject() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL"),
                        ExtractedField.missing(InvoiceFields.TOTAL_AMOUNT)));

        String expected = String.join("\n",
                "{",
                "  \"supplier\": \"ACME SRL\",",
                "  \"totalAmount\": null",
                "}");
        assertEquals(expected, formatter.format(data));
    }

    @Test
    @DisplayName("escapes quotes inside a value")
    void escapesQuotes() {
        InvoiceData data = InvoiceData.of(
                RecognizedText.EMPTY,
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "SC \"ACME\" SRL")));

        assertEquals("{\n  \"supplier\": \"SC \\\"ACME\\\" SRL\"\n}", formatter.format(data));
    }
}
