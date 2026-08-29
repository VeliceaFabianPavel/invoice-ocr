package com.invoiceocr.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.support.TestMessageSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlainTextInvoiceReportFormatter")
class PlainTextInvoiceReportFormatterTest {

    private final InvoiceReportFormatter formatter =
            new PlainTextInvoiceReportFormatter(new TestMessageSource());

    @Test
    @DisplayName("prints one line per field, in display order")
    void printsFieldsInOrder() {
        String report = formatter.format(sample());

        List<String> valueLines = report.lines().filter(line -> line.contains(" : ")).toList();
        assertEquals(2, valueLines.size());
        assertTrue(valueLines.get(0).startsWith("field.supplier"), valueLines.get(0));
        assertTrue(valueLines.get(0).endsWith("ACME SRL"), valueLines.get(0));
        assertTrue(valueLines.get(1).endsWith("1190.00"), valueLines.get(1));
    }

    @Test
    @DisplayName("renders a missing field with the placeholder instead of omitting it")
    void rendersMissingFields() {
        InvoiceData data = InvoiceData.of(RecognizedText.EMPTY,
                List.of(ExtractedField.missing(InvoiceFields.SUPPLIER)));

        assertTrue(formatter.format(data).contains("report.missingValue"));
    }

    @Test
    @DisplayName("aligns the labels into one column")
    void alignsLabels() {
        String report = formatter.format(sample());

        List<Integer> separatorColumns = report.lines()
                .filter(line -> line.contains(" : "))
                .map(line -> line.indexOf(" : "))
                .distinct()
                .toList();
        assertEquals(1, separatorColumns.size());
    }

    private static InvoiceData sample() {
        return InvoiceData.of(
                RecognizedText.of("raw"),
                List.of(
                        ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1190.00"),
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")));
    }
}
