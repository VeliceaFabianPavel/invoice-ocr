package com.invoiceocr.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.support.TestMessageSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@DisplayName("Export formats")
class StructuredFormattersTest {

    private static final InvoiceData DATA = InvoiceData.of(
            RecognizedText.of("Furnizor: ACME SRL\nTotal de plata 1.190,00"),
            List.of(
                    ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL"),
                    ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1190.00"),
                    ExtractedField.missing(InvoiceFields.VAT_AMOUNT)));

    @Nested
    @DisplayName("XML")
    class Xml {

        private final InvoiceReportFormatter formatter = new XmlInvoiceReportFormatter();

        @Test
        @DisplayName("produces a document a real XML parser accepts")
        void isWellFormed() throws Exception {
            String xml = formatter.format(DATA);

            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();

            assertEquals("invoice", document.getDocumentElement().getTagName());
            NodeList fields = document.getElementsByTagName("field");
            assertEquals(3, fields.getLength());
            assertEquals("supplier", ((Element) fields.item(0)).getAttribute("key"));
            assertEquals("ACME SRL", fields.item(0).getTextContent());
        }

        @Test
        @DisplayName("marks a field that was not found, rather than dropping it")
        void keepsMissingFields() throws Exception {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            formatter.format(DATA).getBytes(StandardCharsets.UTF_8)));

            // Fields come out in display order: supplier, VAT, total.
            Element vat = (Element) document.getElementsByTagName("field").item(1);
            assertEquals("vatAmount", vat.getAttribute("key"));
            assertEquals("false", vat.getAttribute("found"));
            assertEquals("", vat.getTextContent());
        }

        @Test
        @DisplayName("escapes markup that appears in a recognised value")
        void escapesMarkup() throws Exception {
            InvoiceData risky = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "Ben & Co <ltd>")));

            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            formatter.format(risky).getBytes(StandardCharsets.UTF_8)));

            assertEquals("Ben & Co <ltd>",
                    document.getElementsByTagName("field").item(0).getTextContent());
        }
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        private final InvoiceReportFormatter formatter = new CsvInvoiceReportFormatter();

        @Test
        @DisplayName("writes a header row of keys and one row of values")
        void writesHeaderAndValues() {
            String[] lines = formatter.format(DATA).split("\r\n");

            assertEquals(2, lines.length);
            assertEquals("supplier,vatAmount,totalAmount", lines[0]);
            assertEquals("ACME SRL,,1190.00", lines[1]);
        }

        @Test
        @DisplayName("quotes a value containing a comma or a quote")
        void quotesWhereNeeded() {
            InvoiceData risky = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME, \"the\" one")));

            assertTrue(formatter.format(risky).contains("\"ACME, \"\"the\"\" one\""));
        }
    }

    @Nested
    @DisplayName("Markdown")
    class Markdown {

        private final InvoiceReportFormatter formatter =
                new MarkdownInvoiceReportFormatter(new TestMessageSource());

        @Test
        @DisplayName("builds a table and appends the raw text as a code block")
        void buildsTableAndRawText() {
            String markdown = formatter.format(DATA);

            assertTrue(markdown.startsWith("# report.header"));
            assertTrue(markdown.contains("|---|---|"));
            assertTrue(markdown.contains("| field.supplier | ACME SRL |"));
            assertTrue(markdown.contains("```text"));
            assertTrue(markdown.contains("Furnizor: ACME SRL"));
        }

        @Test
        @DisplayName("escapes a pipe so it cannot break the table")
        void escapesPipes() {
            InvoiceData risky = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME | SRL")));

            assertTrue(formatter.format(risky).contains("ACME \\| SRL"));
        }
    }

    @Nested
    @DisplayName("HTML")
    class Html {

        private final InvoiceReportFormatter formatter =
                new HtmlInvoiceReportFormatter(new TestMessageSource());

        @Test
        @DisplayName("is a standalone page with the styles inside it")
        void isSelfContained() {
            String html = formatter.format(DATA);

            assertTrue(html.startsWith("<!doctype html>"));
            assertTrue(html.contains("<style>"));
            assertTrue(html.trim().endsWith("</html>"));
            assertTrue(html.contains("ACME SRL"));
            assertTrue(html.contains("<pre>"), "the raw OCR text belongs in the page");
        }

        @Test
        @DisplayName("escapes markup coming out of OCR")
        void escapesMarkup() {
            InvoiceData risky = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "<script>alert(1)</script>")));

            String html = formatter.format(risky);

            assertTrue(html.contains("&lt;script&gt;"));
            assertTrue(html.contains("alert(1)"));
            assertTrue(html.indexOf("<script>") < 0, "no raw script tag may survive");
        }
    }

    @Nested
    @DisplayName("Plain text with raw appendix")
    class PlainTextWithRaw {

        private final InvoiceReportFormatter formatter = new RawTextAppendingFormatter(
                new PlainTextInvoiceReportFormatter(new TestMessageSource()), new TestMessageSource());

        @Test
        @DisplayName("keeps the on-screen report and adds the raw text under it")
        void appendsRawText() {
            String text = formatter.format(DATA);

            assertTrue(text.contains("field.supplier"));
            assertTrue(text.contains("report.rawText"));
            assertTrue(text.indexOf("field.supplier") < text.indexOf("report.rawText"));
            assertTrue(text.contains("Total de plata 1.190,00"));
        }

        @Test
        @DisplayName("adds nothing when there was no raw text")
        void skipsEmptyRawText() {
            InvoiceData empty = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL")));

            assertTrue(formatter.format(empty).indexOf("report.rawText") < 0);
        }
    }
}
