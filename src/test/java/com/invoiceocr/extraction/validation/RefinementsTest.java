package com.invoiceocr.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.text.SearchText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The passes that look at the whole invoice once the rules have finished.
 *
 * <p>Each is driven with data assembled by hand rather than parsed, so a failure
 * here is a failure of the reasoning and never of a pattern somewhere else.</p>
 */
@DisplayName("Refinements")
class RefinementsTest {

    /** An invoice carrying exactly the fields given, and nothing else. */
    private static InvoiceData invoice(String text, ExtractedField... fields) {
        List<ExtractedField> all = new ArrayList<>();
        for (FieldDefinition definition : InvoiceFields.ALL) {
            ExtractedField supplied = null;
            for (ExtractedField field : fields) {
                if (field.definition().equals(definition)) {
                    supplied = field;
                }
            }
            all.add(supplied == null ? ExtractedField.missing(definition) : supplied);
        }
        return InvoiceData.of(RecognizedText.of(text), all);
    }

    private static ExtractedField amount(FieldDefinition field, String value) {
        return ExtractedField.of(field, value, FieldConfidence.LABELLED, "test");
    }

    private static Optional<String> valueOf(InvoiceData data, FieldDefinition field) {
        return data.valueOf(field);
    }

    @Nested
    @DisplayName("Arithmetic")
    class Arithmetic {

        private final ArithmeticRefinement refinement = new ArithmeticRefinement();

        private InvoiceData refine(InvoiceData data) {
            return refinement.refine(data, SearchText.of(data.source()));
        }

        @Test
        @DisplayName("three figures that add up confirm one another")
        void confirmsAConsistentTotalsBlock() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.NET_AMOUNT, "1000.00"),
                    amount(InvoiceFields.VAT_AMOUNT, "190.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "1190.00")));

            for (FieldDefinition field : InvoiceFields.MONEY) {
                assertEquals(FieldConfidence.VERIFIED,
                        refined.field(field).orElseThrow().confidence(),
                        field.key() + " should be corroborated by the other two");
            }
        }

        @Test
        @DisplayName("a missing VAT follows from the net and the total")
        void derivesTheMissingVat() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.NET_AMOUNT, "2000.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "2380.00")));

            assertEquals(Optional.of("380.00"), valueOf(refined, InvoiceFields.VAT_AMOUNT));
            assertEquals(FieldConfidence.DERIVED,
                    refined.field(InvoiceFields.VAT_AMOUNT).orElseThrow().confidence());
        }

        @Test
        @DisplayName("a missing total follows from the net and the VAT")
        void derivesTheMissingTotal() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.NET_AMOUNT, "500.00"),
                    amount(InvoiceFields.VAT_AMOUNT, "95.00")));

            assertEquals(Optional.of("595.00"), valueOf(refined, InvoiceFields.TOTAL_AMOUNT));
        }

        @Test
        @DisplayName("a missing net follows from the total and the VAT")
        void derivesTheMissingNet() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.VAT_AMOUNT, "190.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "1190.00")));

            assertEquals(Optional.of("1000.00"), valueOf(refined, InvoiceFields.NET_AMOUNT));
        }

        @Test
        @DisplayName("a total that lost a digit is recomputed from the pair that makes sense")
        void correctsAMisreadTotal() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.NET_AMOUNT, "3000.00"),
                    amount(InvoiceFields.VAT_AMOUNT, "570.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "370.00")));

            assertEquals(Optional.of("3570.00"), valueOf(refined, InvoiceFields.TOTAL_AMOUNT));
            assertEquals(Optional.of("3000.00"), valueOf(refined, InvoiceFields.NET_AMOUNT),
                    "the figures that agreed are left alone");
        }

        @Test
        @DisplayName("a gross figure with a printed rate yields both other amounts")
        void splitsAGrossFigure() {
            InvoiceData refined = refine(invoice("Suma include TVA 19%\nTotal de plata 119,00",
                    amount(InvoiceFields.TOTAL_AMOUNT, "119.00")));

            assertEquals(Optional.of("100.00"), valueOf(refined, InvoiceFields.NET_AMOUNT));
            assertEquals(Optional.of("19.00"), valueOf(refined, InvoiceFields.VAT_AMOUNT));
        }

        @Test
        @DisplayName("a gross figure with no rate on the page is left as it is")
        void willNotGuessARate() {
            InvoiceData refined = refine(invoice("Total de plata 119,00",
                    amount(InvoiceFields.TOTAL_AMOUNT, "119.00")));

            assertTrue(valueOf(refined, InvoiceFields.NET_AMOUNT).isEmpty());
            assertTrue(valueOf(refined, InvoiceFields.VAT_AMOUNT).isEmpty());
        }

        @Test
        @DisplayName("a VAT larger than the total is not a VAT, and is replaced")
        void discardsAnImpossibleComponent() {
            InvoiceData refined = refine(invoice("TVA 19%\nTotal de plata 119,00",
                    amount(InvoiceFields.VAT_AMOUNT, "119.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "119.00")));

            assertEquals(Optional.of("19.00"), valueOf(refined, InvoiceFields.VAT_AMOUNT));
            assertEquals(Optional.of("100.00"), valueOf(refined, InvoiceFields.NET_AMOUNT));
        }

        @Test
        @DisplayName("figures that cannot be reconciled are kept, and flagged")
        void flagsWhatItCannotReconcile() {
            InvoiceData refined = refine(invoice("",
                    amount(InvoiceFields.NET_AMOUNT, "1000.00"),
                    amount(InvoiceFields.VAT_AMOUNT, "377.00"),
                    amount(InvoiceFields.TOTAL_AMOUNT, "1500.00")));

            assertEquals(Optional.of("1000.00"), valueOf(refined, InvoiceFields.NET_AMOUNT),
                    "nothing is thrown away");
            assertEquals(3, refined.needingReview().size(),
                    "all three are marked for the user to check");
        }

        @Test
        @DisplayName("an invoice with no amounts at all is returned untouched")
        void leavesAnEmptyInvoiceAlone() {
            InvoiceData empty = invoice("");
            assertEquals(0, refine(empty).recognizedCount());
        }
    }

    @Nested
    @DisplayName("Dates")
    class Dates {

        private final DateRefinement refinement = new DateRefinement();

        private InvoiceData refine(String text, ExtractedField... fields) {
            InvoiceData data = invoice(text, fields);
            return refinement.refine(data, SearchText.of(data.source()));
        }

        private ExtractedField date(FieldDefinition field, String value) {
            return ExtractedField.of(field, value, FieldConfidence.LABELLED, "test");
        }

        @Test
        @DisplayName("a payment term in days becomes the date the invoice falls due")
        void derivesTheDueDateFromTheTerm() {
            InvoiceData refined = refine("Termen de plata: 30 zile",
                    date(InvoiceFields.ISSUE_DATE, "01.10.2024"));

            assertEquals(Optional.of("31.10.2024"), valueOf(refined, InvoiceFields.DUE_DATE));
        }

        @Test
        @DisplayName("the term is measured from the issue date across a month boundary")
        void crossesMonthBoundaries() {
            InvoiceData refined = refine("Termen de plata 45 zile",
                    date(InvoiceFields.ISSUE_DATE, "20.12.2024"));

            assertEquals(Optional.of("03.02.2025"), valueOf(refined, InvoiceFields.DUE_DATE));
        }

        @Test
        @DisplayName("a printed due date is preferred to a computed one")
        void keepsAPrintedDueDate() {
            InvoiceData refined = refine("Termen de plata 30 zile",
                    date(InvoiceFields.ISSUE_DATE, "01.10.2024"),
                    date(InvoiceFields.DUE_DATE, "15.10.2024"));

            assertEquals(Optional.of("15.10.2024"), valueOf(refined, InvoiceFields.DUE_DATE));
        }

        @Test
        @DisplayName("an absurd term is ignored rather than believed")
        void ignoresAnAbsurdTerm() {
            InvoiceData refined = refine("Termen de plata 900 zile",
                    date(InvoiceFields.ISSUE_DATE, "01.10.2024"));

            assertTrue(valueOf(refined, InvoiceFields.DUE_DATE).isEmpty());
        }

        @Test
        @DisplayName("a due date before the issue date marks both for checking")
        void flagsAnImpossibleOrdering() {
            InvoiceData refined = refine("",
                    date(InvoiceFields.ISSUE_DATE, "01.10.2024"),
                    date(InvoiceFields.DUE_DATE, "01.09.2024"));

            assertEquals(2, refined.needingReview().size());
        }

        @Test
        @DisplayName("no issue date means there is nothing to measure a term from")
        void needsAnIssueDate() {
            InvoiceData refined = refine("Termen de plata 30 zile");
            assertTrue(valueOf(refined, InvoiceFields.DUE_DATE).isEmpty());
        }
    }
}
