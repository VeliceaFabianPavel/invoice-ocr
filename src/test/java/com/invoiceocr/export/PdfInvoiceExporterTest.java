package com.invoiceocr.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.support.TestMessageSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The PDF is assembled by hand, so these tests check the parts a reader relies
 * on: the header, the cross-reference offsets, the trailer and the page tree.
 * A wrong byte offset produces a file that opens as blank or not at all, and
 * that is exactly what a unit test can catch cheaply.
 */
@DisplayName("PdfInvoiceExporter")
class PdfInvoiceExporterTest {

    private final PdfInvoiceExporter exporter = new PdfInvoiceExporter(new TestMessageSource());

    @Test
    @DisplayName("writes a PDF 1.4 header and an EOF marker")
    void writesHeaderAndTrailer() throws IOException {
        String pdf = latin1(export(sample("raw text")));

        assertTrue(pdf.startsWith("%PDF-1.4"), "should start with the version header");
        assertTrue(pdf.trim().endsWith("%%EOF"), "should end with the EOF marker");
        assertTrue(pdf.contains("/Type /Catalog"));
        assertTrue(pdf.contains("/Type /Pages"));
    }

    @Test
    @DisplayName("every cross-reference offset points at the object it claims")
    void crossReferenceTableIsCorrect() throws IOException {
        byte[] bytes = export(sample("raw text"));
        String pdf = latin1(bytes);

        Matcher startxref = Pattern.compile("startxref\\s+(\\d+)").matcher(pdf);
        assertTrue(startxref.find(), "startxref must be present");
        int xrefOffset = Integer.parseInt(startxref.group(1));
        assertTrue(pdf.startsWith("xref", xrefOffset), "startxref must point at the xref table");

        Matcher header = Pattern.compile("xref\\s+0 (\\d+)").matcher(pdf);
        assertTrue(header.find(xrefOffset));
        int size = Integer.parseInt(header.group(1));

        Matcher entries = Pattern.compile("(\\d{10}) 00000 n").matcher(pdf.substring(xrefOffset));
        int object = 1;
        while (entries.find()) {
            int offset = Integer.parseInt(entries.group(1));
            assertTrue(pdf.startsWith(object + " 0 obj", offset),
                    "object " + object + " should begin at byte " + offset);
            object++;
        }
        assertEquals(size, object, "the table should list every object exactly once");
    }

    @Test
    @DisplayName("declares as many page objects as the page tree counts")
    void pageTreeMatchesPageObjects() throws IOException {
        String pdf = latin1(export(sample("raw text")));

        Matcher count = Pattern.compile("/Count (\\d+)").matcher(pdf);
        assertTrue(count.find());
        int declared = Integer.parseInt(count.group(1));

        int actual = countOccurrences(pdf, "/Type /Page ");
        assertEquals(declared, actual);
    }

    @Test
    @DisplayName("spills long OCR text onto further pages")
    void paginatesLongDocuments() throws IOException {
        String longText = ("A line of recognised text\n").repeat(200);
        String pdf = latin1(export(sample(longText)));

        Matcher count = Pattern.compile("/Count (\\d+)").matcher(pdf);
        assertTrue(count.find());
        assertTrue(Integer.parseInt(count.group(1)) > 1, "200 lines should not fit on one page");
    }

    @Test
    @DisplayName("transliterates Romanian letters the built-in fonts cannot show")
    void transliteratesDiacritics() throws IOException {
        InvoiceData data = InvoiceData.of(
                RecognizedText.of(""),
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "SC VÂNZĂTOR ȘI FIȚĂ SRL")));

        String pdf = latin1(export(data));

        assertTrue(pdf.contains("SC VÂNZATOR SI FITA SRL"),
                "a-breve, s-comma and t-comma should become plain letters");
    }

    @Test
    @DisplayName("escapes the characters that would end a PDF string early")
    void escapesParenthesesAndBackslashes() throws IOException {
        InvoiceData data = InvoiceData.of(
                RecognizedText.of(""),
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME (Romania) \\ Ltd")));

        String pdf = latin1(export(data));

        assertTrue(pdf.contains("ACME \\(Romania\\) \\\\ Ltd"));
    }

    @Test
    @DisplayName("sets the goods table, its columns aligned by the monospaced font")
    void writesTheGoodsTable() throws IOException {
        InvoiceData data = InvoiceData.of(
                RecognizedText.of(""),
                List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")),
                List.of(com.invoiceocr.domain.LineItem.of("Ciment Portland", "10", "32.00", "320.00"),
                        com.invoiceocr.domain.LineItem.of("Manopera", null, null, "870.00")));

        String pdf = latin1(export(data));

        assertTrue(pdf.contains("report.items.title"));
        assertTrue(pdf.contains("Ciment Portland"));
        assertTrue(pdf.contains("Manopera"));
        assertTrue(pdf.contains("320.00"));
    }

    @Test
    @DisplayName("marks an inferred value and prints the explanation once")
    void marksInferredValues() throws IOException {
        InvoiceData data = InvoiceData.of(
                RecognizedText.of(""),
                List.of(ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1190.00",
                        com.invoiceocr.domain.FieldConfidence.INFERRED, "shape-largest")));

        String pdf = latin1(export(data));

        assertTrue(pdf.contains("1190.00 report.reviewMark"));
        assertTrue(pdf.contains("report.reviewHint"));
    }

    private byte[] export(InvoiceData data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(data, out);
        return out.toByteArray();
    }

    private static InvoiceData sample(String rawText) {
        return InvoiceData.of(
                RecognizedText.of(rawText),
                List.of(
                        ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL"),
                        ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1190.00"),
                        ExtractedField.missing(InvoiceFields.VAT_AMOUNT)));
    }

    /** Byte-for-char view, so string offsets equal file offsets. */
    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
