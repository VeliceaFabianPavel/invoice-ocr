package com.invoiceocr.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.config.ReportSettings;
import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.LineItem;
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

/**
 * What the reports gained in 1.2: the rows of the goods table, and a mark
 * against every figure that was worked out rather than read.
 */
@DisplayName("What a report says about its own reliability")
class ReportEnrichmentTest {

    private static final InvoiceData DATA = InvoiceData.of(
            RecognizedText.of("Furnizor: ACME SRL"),
            List.of(
                    ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL",
                            FieldConfidence.LABELLED, "labelled"),
                    ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1190.00",
                            FieldConfidence.INFERRED, "shape-largest"),
                    ExtractedField.missing(InvoiceFields.VAT_AMOUNT)),
            List.of(LineItem.of("Ciment Portland", "10", "32.00", "320.00"),
                    LineItem.of("Manopera", null, null, "870.00")));

    private static final InvoiceData CERTAIN = InvoiceData.of(
            RecognizedText.EMPTY,
            List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "ACME SRL",
                    FieldConfidence.LABELLED, "labelled")));

    @Nested
    @DisplayName("Plain text")
    class PlainText {

        private final InvoiceReportFormatter formatter =
                new PlainTextInvoiceReportFormatter(new TestMessageSource());

        @Test
        @DisplayName("marks an inferred value and explains the mark once")
        void marksInferredValues() {
            String report = formatter.format(DATA);

            String totalLine = report.lines()
                    .filter(line -> line.contains("1190.00"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(totalLine.contains("report.reviewMark"), totalLine);
            assertTrue(report.contains("report.reviewHint"));
        }

        @Test
        @DisplayName("says nothing about reliability when nothing is in doubt")
        void staysQuietWhenNothingIsDoubtful() {
            String report = formatter.format(CERTAIN);

            assertFalse(report.contains("report.reviewMark"));
            assertFalse(report.contains("report.reviewHint"));
        }

        @Test
        @DisplayName("prints the goods table under the fields")
        void printsTheTable() {
            String report = formatter.format(DATA);

            assertTrue(report.contains("report.items.title"));
            assertTrue(report.contains("Ciment Portland"));
            assertTrue(report.contains("Manopera"));
        }

        @Test
        @DisplayName("an absent column shows the placeholder, not a blank")
        void showsAbsentColumns() {
            String row = formatter.format(DATA).lines()
                    .filter(line -> line.startsWith("Manopera"))
                    .findFirst()
                    .orElseThrow();

            assertTrue(row.contains("report.missingValue"), row);
        }

        @Test
        @DisplayName("both additions can be switched off")
        void honoursTheSettings() {
            InvoiceReportFormatter plain = new PlainTextInvoiceReportFormatter(
                    new TestMessageSource(), new ReportSettings() {
                        @Override
                        public boolean showConfidence() {
                            return false;
                        }

                        @Override
                        public boolean includeLineItems() {
                            return false;
                        }
                    });
            String report = plain.format(DATA);

            assertFalse(report.contains("report.reviewMark"));
            assertFalse(report.contains("Ciment Portland"));
            assertTrue(report.contains("1190.00"), "the value itself is still reported");
        }
    }

    @Nested
    @DisplayName("Markdown")
    class Markdown {

        private final InvoiceReportFormatter formatter =
                new MarkdownInvoiceReportFormatter(new TestMessageSource());

        @Test
        @DisplayName("marks an inferred value inside its cell")
        void marksInferredValues() {
            assertTrue(formatter.format(DATA).contains("1190.00 report.reviewMark"));
        }

        @Test
        @DisplayName("adds the goods table as a table of its own")
        void addsTheTable() {
            String report = formatter.format(DATA);

            assertTrue(report.contains("## report.items.title"));
            assertTrue(report.contains("|---|---:|---:|---:|"));
            assertTrue(report.contains("| Ciment Portland | 10 | 32.00 | 320.00 |"));
        }
    }

    @Nested
    @DisplayName("HTML")
    class Html {

        private final InvoiceReportFormatter formatter =
                new HtmlInvoiceReportFormatter(new TestMessageSource());

        @Test
        @DisplayName("styles a doubtful value apart from a missing one")
        void stylesDoubtAndAbsenceApart() {
            String html = formatter.format(DATA);

            assertTrue(html.contains("class=\"review\""), "the inferred total");
            assertTrue(html.contains("class=\"missing\""), "the VAT that was never found");
        }

        @Test
        @DisplayName("puts the strategy behind a value in its tooltip")
        void explainsOnHover() {
            assertTrue(formatter.format(DATA).contains("title=\"shape-largest (0.35)\""));
        }

        @Test
        @DisplayName("renders the goods table with its figures right-aligned")
        void rendersTheTable() {
            String html = formatter.format(DATA);

            assertTrue(html.contains("<table class=\"items\">"));
            assertTrue(html.contains("<td class=\"num\">320.00</td>"));
        }
    }

    @Nested
    @DisplayName("XML")
    class Xml {

        private final InvoiceReportFormatter formatter = new XmlInvoiceReportFormatter();

        private Element parse(String xml) throws Exception {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();
            return document.getDocumentElement();
        }

        @Test
        @DisplayName("carries confidence and provenance as attributes, so 1.1 readers still work")
        void carriesConfidenceAsAttributes() throws Exception {
            Element root = parse(formatter.format(DATA));
            Element total = null;
            var fields = root.getElementsByTagName("field");
            for (int i = 0; i < fields.getLength(); i++) {
                Element field = (Element) fields.item(i);
                if ("totalAmount".equals(field.getAttribute("key"))) {
                    total = field;
                }
            }

            assertTrue(total != null);
            assertEquals("0.35", total.getAttribute("confidence"));
            assertEquals("shape-largest", total.getAttribute("strategy"));
            assertEquals("true", total.getAttribute("review"));
        }

        @Test
        @DisplayName("a missing field carries no confidence to be misread as zero")
        void leavesMissingFieldsBare() throws Exception {
            Element root = parse(formatter.format(DATA));
            var fields = root.getElementsByTagName("field");
            for (int i = 0; i < fields.getLength(); i++) {
                Element field = (Element) fields.item(i);
                if ("vatAmount".equals(field.getAttribute("key"))) {
                    assertEquals("", field.getAttribute("confidence"));
                }
            }
        }

        @Test
        @DisplayName("carries the goods table as an element of its own")
        void carriesTheTable() throws Exception {
            Element root = parse(formatter.format(DATA));
            Element items = (Element) root.getElementsByTagName("lineItems").item(0);

            assertEquals("2", items.getAttribute("count"));
            Element first = (Element) items.getElementsByTagName("item").item(0);
            assertEquals("Ciment Portland", first.getTextContent());
            assertEquals("320.00", first.getAttribute("value"));
        }

        @Test
        @DisplayName("an invoice with no table has no lineItems element at all")
        void omitsAnEmptyTable() throws Exception {
            Element root = parse(formatter.format(CERTAIN));

            assertEquals(0, root.getElementsByTagName("lineItems").getLength());
        }
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        private final InvoiceReportFormatter formatter = new CsvInvoiceReportFormatter();

        @Test
        @DisplayName("stays one header row and one value row, so exports still stack")
        void staysStackable() {
            List<String> lines = formatter.format(DATA).lines().toList();

            assertEquals(2, lines.size());
            assertTrue(lines.get(0).startsWith("supplier,"));
            assertTrue(lines.get(1).startsWith("ACME SRL,"));
        }

        @Test
        @DisplayName("gains a column for every field the catalog gained")
        void carriesTheNewFields() {
            InvoiceData full = InvoiceData.of(RecognizedText.EMPTY,
                    InvoiceFields.ALL.stream().map(ExtractedField::missing).toList());

            String header = formatter.format(full).lines().findFirst().orElseThrow();

            assertEquals(InvoiceFields.ALL.size(), header.split(",", -1).length);
            assertTrue(header.contains("iban"));
            assertTrue(header.contains("netAmount"));
        }
    }
}
