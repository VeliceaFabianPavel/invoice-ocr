package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.extraction.text.OcrDigits;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Extraction strategies")
class ExtractionStrategiesTest {

    @Nested
    @DisplayName("A label with its value nearby")
    class Labelled {

        @Test
        @DisplayName("reads a value beside its label")
        void readsValueOnTheSameLine() {
            SearchText text = SearchText.of("Data facturii: 05.03.2024\n");

            assertEquals(Optional.of("05.03.2024"),
                    LabelledValueExtractor.sameLine("Data facturii" + ValuePatterns.SEPARATOR,
                            ValuePatterns.date()).extractValue(text));
        }

        @Test
        @DisplayName("reads a value from the row under a column heading")
        void readsValueBelowAHeading() {
            SearchText text = SearchText.of("""
                    Nr. factura     Data emiterii     Termen
                    GML-7781        02.02.2024        02.03.2024
                    """);

            assertEquals(Optional.of("02.02.2024"),
                    LabelledValueExtractor.within("Data emiterii" + ValuePatterns.SEPARATOR,
                            ValuePatterns.date(), 2).extractValue(text));
        }

        @Test
        @DisplayName("stays inside its line budget instead of wandering down the page")
        void respectsTheLineBudget() {
            SearchText text = SearchText.of("Data emiterii\n\n\n\n05.03.2024\n");

            assertTrue(LabelledValueExtractor.within("Data emiterii", ValuePatterns.date(), 1)
                    .extractValue(text).isEmpty());
        }

        @Test
        @DisplayName("tries the next occurrence when a label leads nowhere")
        void triesEveryOccurrence() {
            SearchText text = SearchText.of("Total\nsome prose here\nTotal de plata 1.190,00\n");

            assertEquals(Optional.of("1.190,00"),
                    LabelledValueExtractor.sameLine("Total" + ValuePatterns.SEPARATOR,
                            ValuePatterns.amount()).extractValue(text));
        }
    }

    @Nested
    @DisplayName("Choosing between candidates")
    class Shapes {

        @Test
        @DisplayName("the largest amount is the total when nothing is labelled")
        void picksTheLargestAmount() {
            SearchText text = SearchText.of("""
                    Ambalaje    12.400,00
                    Manopera     1.100,00
                    TOTAL       16.065,00
                    """);

            assertEquals(Optional.of("16.065,00"),
                    ValueShapeExtractor.largest(ValuePatterns.amount()).extractValue(text));
        }

        @Test
        @DisplayName("a gate keeps a guess off a page that is not an invoice")
        void gatesTheGuess() {
            SearchText notAnInvoice = SearchText.of("random 8B8B8 numbers 42,00\n");

            FieldExtractor gated = new ContextGatedExtractor(
                    ValueShapeExtractor.largest(ValuePatterns.amount()), "\\btotal\\b");

            assertTrue(gated.extractValue(notAnInvoice).isEmpty());
        }
    }

    @Nested
    @DisplayName("Telling the two companies apart")
    class Regions {

        private static final SearchText BUYER_FIRST = SearchText.of("""
                CUMPARATOR: SC OMEGA RETAIL SRL
                CUI: RO 5550001

                FURNIZOR: SC EPSILON SERVICE SRL
                CUI: RO 6660002
                """);

        @Test
        @DisplayName("the supplier's block starts at its own marker, even when the buyer is printed first")
        void findsTheSupplierBlock() {
            TextRegion supplier = DocumentRegions.supplier(BUYER_FIRST);

            assertTrue(BUYER_FIRST.original().substring(supplier.start()).startsWith("FURNIZOR"));
        }

        @Test
        @DisplayName("scoping keeps the buyer's fiscal code out of the supplier's field")
        void scopingPicksTheRightCode() {
            FieldExtractor scoped = RegionScopedExtractor.inSupplierBlock(
                    ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()));

            assertEquals(Optional.of("RO 6660002"), scoped.extractValue(BUYER_FIRST));
        }

        @Test
        @DisplayName("without scoping the first code on the page wins, which is the buyer's")
        void withoutScopingTheBuyerWins() {
            assertEquals(Optional.of("RO 5550001"),
                    ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()).extractValue(BUYER_FIRST));
        }
    }

    @Nested
    @DisplayName("Company names")
    class Companies {

        @Test
        @DisplayName("finds a name that has no label at all")
        void findsLetterheadName() {
            SearchText text = SearchText.of("""
                    SC SIGMA MEDICAL SRL
                    Str. Sanatatii 8, Timisoara
                    """);

            assertEquals(Optional.of("SC SIGMA MEDICAL SRL"), new CompanyNameExtractor().extractValue(text));
        }

        @Test
        @DisplayName("strips a label printed in front of the name")
        void stripsTheLabel() {
            SearchText text = SearchText.of("Furnizor: SC ALFA CONSTRUCT SRL\n");

            assertEquals(Optional.of("SC ALFA CONSTRUCT SRL"), new CompanyNameExtractor().extractValue(text));
        }

        @Test
        @DisplayName("skips an address line that happens to mention a company")
        void skipsAddresses() {
            SearchText text = SearchText.of("""
                    Str. Fabricii 2, langa SC VECHI SRL
                    SC ALFA CONSTRUCT SRL
                    """);

            assertEquals(Optional.of("SC ALFA CONSTRUCT SRL"), new CompanyNameExtractor().extractValue(text));
        }
    }

    @Nested
    @DisplayName("Repairing OCR digits")
    class Digits {

        @Test
        @DisplayName("repairs a wholly numeric value")
        void repairsNumbers() {
            assertEquals("1.428,00", OcrDigits.repair("l.428,OO"));
            assertEquals("09.09.2024", OcrDigits.repair("O9.O9.2O24"));
        }

        @Test
        @DisplayName("repairs the numeric run of a document number, not its series")
        void repairsOnlyNumericRuns() {
            assertEquals("ZT-0091", OcrDigits.repairNumericRuns("ZT-OO91"));
        }

        @Test
        @DisplayName("leaves a mixed run alone rather than inventing digits")
        void leavesMixedRunsAlone() {
            assertEquals("AB123", OcrDigits.repairNumericRuns("AB123"));
            assertEquals("SB-100", OcrDigits.repairNumericRuns("SB-100"));
        }
    }

    @Nested
    @DisplayName("Reading a table by its columns")
    class Columns {

        private static final SearchText HEADINGS = SearchText.of("""
                Nr. factura     Data emiterii     Termen de plata
                GML-7781        02.02.2024        02.03.2024
                """);

        @Test
        @DisplayName("a heading takes the value in its own column, not the first one below it")
        void followsTheColumn() {
            assertEquals(Optional.of("02.03.2024"),
                    LabelledValueExtractor.within("Termen de plata" + ValuePatterns.SEPARATOR,
                            ValuePatterns.date(), 2).extractValue(HEADINGS));
        }

        @Test
        @DisplayName("each heading on the row finds its own value")
        void readsEveryColumn() {
            assertEquals(Optional.of("02.02.2024"),
                    LabelledValueExtractor.within("Data emiterii" + ValuePatterns.SEPARATOR,
                            ValuePatterns.date(), 2).extractValue(HEADINGS));
        }

        @Test
        @DisplayName("a value on the label's own line still wins over any column below")
        void prefersTheOwnLine() {
            SearchText text = SearchText.of("""
                    Data facturii: 05.03.2024   Termen
                    01.01.2020
                    """);

            assertEquals(Optional.of("05.03.2024"),
                    LabelledValueExtractor.within("Data facturii" + ValuePatterns.SEPARATOR,
                            ValuePatterns.date(), 2).extractValue(text));
        }

        @Test
        @DisplayName("a nearer line beats a better-aligned column further away")
        void prefersTheNearerLine() {
            SearchText text = SearchText.of("""
                    Data emiterii
                        05.03.2024
                    01.01.2020
                    """);

            assertEquals(Optional.of("05.03.2024"),
                    LabelledValueExtractor.within("Data emiterii", ValuePatterns.date(), 2)
                            .extractValue(text));
        }
    }
}
