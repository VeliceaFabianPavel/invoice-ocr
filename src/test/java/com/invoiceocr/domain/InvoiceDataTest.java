package com.invoiceocr.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The invoice model")
class InvoiceDataTest {

    @Nested
    @DisplayName("A field and how it was found")
    class Fields {

        @Test
        @DisplayName("a missing field has no confidence, whatever it was given")
        void missingFieldsScoreZero() {
            assertEquals(0.0, ExtractedField.missing(InvoiceFields.IBAN).confidence());
            assertEquals(0.0, ExtractedField.of(InvoiceFields.IBAN, "  ",
                    FieldConfidence.VERIFIED, "test").confidence());
        }

        @Test
        @DisplayName("confidence outside the scale is brought back onto it")
        void clampsConfidence() {
            assertEquals(1.0, ExtractedField.of(InvoiceFields.SUPPLIER, "x", 4.0, "test").confidence());
            assertEquals(0.0, ExtractedField.of(InvoiceFields.SUPPLIER, "x", -2.0, "test").confidence());
        }

        @Test
        @DisplayName("a value found by a strategy that guesses is marked for review")
        void marksAGuess() {
            assertTrue(ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "100.00",
                    FieldConfidence.INFERRED, "shape-largest").needsReview());
            assertFalse(ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "100.00",
                    FieldConfidence.LABELLED, "labelled").needsReview());
        }

        @Test
        @DisplayName("a missing field is not marked for review; it is simply absent")
        void doesNotMarkAbsence() {
            assertFalse(ExtractedField.missing(InvoiceFields.TOTAL_AMOUNT).needsReview());
        }

        @Test
        @DisplayName("re-rating keeps the value and replaces the verdict")
        void reRates() {
            ExtractedField original = ExtractedField.of(InvoiceFields.SUPPLIER, "SC ALFA SRL",
                    FieldConfidence.SHAPED, "company-line");
            ExtractedField rated = original.ratedAt(FieldConfidence.VERIFIED, "confirmed");

            assertEquals(Optional.of("SC ALFA SRL"), rated.value());
            assertEquals(FieldConfidence.VERIFIED, rated.confidence());
            assertEquals("confirmed", rated.strategy());
        }
    }

    @Nested
    @DisplayName("The catalog")
    class Catalog {

        @Test
        @DisplayName("carries the twelve fields of 1.2, in display order")
        void listsEveryField() {
            assertEquals(12, InvoiceFields.ALL.size());
            assertEquals(InvoiceFields.ALL,
                    InvoiceFields.ALL.stream().sorted().toList());
        }

        @Test
        @DisplayName("looks a field up by the key exports use")
        void findsByKey() {
            assertEquals(Optional.of(InvoiceFields.IBAN), InvoiceFields.byKey("iban"));
            assertEquals(Optional.empty(), InvoiceFields.byKey("nothingLikeThat"));
        }

        @Test
        @DisplayName("names the three amounts that have to add up")
        void namesTheAmounts() {
            assertEquals(List.of(InvoiceFields.NET_AMOUNT, InvoiceFields.VAT_AMOUNT,
                    InvoiceFields.TOTAL_AMOUNT), InvoiceFields.MONEY);
        }
    }

    @Nested
    @DisplayName("The invoice as a whole")
    class Whole {

        private InvoiceData sample() {
            return InvoiceData.of(RecognizedText.of("raw"),
                    List.of(ExtractedField.of(InvoiceFields.SUPPLIER, "SC ALFA SRL",
                                    FieldConfidence.LABELLED, "labelled"),
                            ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "100.00",
                                    FieldConfidence.INFERRED, "shape-largest"),
                            ExtractedField.missing(InvoiceFields.IBAN)),
                    List.of(LineItem.of("Manopera", null, null, "100.00")));
        }

        @Test
        @DisplayName("counts only the fields that carry a value")
        void countsWhatWasFound() {
            assertEquals(2, sample().recognizedCount());
        }

        @Test
        @DisplayName("lists the values worth a second look")
        void listsWhatNeedsReview() {
            assertEquals(List.of(InvoiceFields.TOTAL_AMOUNT),
                    sample().needingReview().stream().map(ExtractedField::definition).toList());
        }

        @Test
        @DisplayName("averages confidence over the fields found, not over the catalog")
        void averagesOverWhatWasFound() {
            double expected = (FieldConfidence.LABELLED + FieldConfidence.INFERRED) / 2;
            assertEquals(expected, sample().averageConfidence(), 1e-9,
                    "an invoice that prints no IBAN is not a worse reading for it");
        }

        @Test
        @DisplayName("an invoice with nothing on it averages zero rather than dividing by it")
        void survivesAnEmptyInvoice() {
            assertEquals(0.0, InvoiceData.empty().averageConfidence());
        }

        @Test
        @DisplayName("carries the rows of the goods table")
        void carriesTheTable() {
            assertTrue(sample().hasLineItems());
            assertEquals(1, sample().lineItems().size());
        }

        @Test
        @DisplayName("fields come back in display order however they were supplied")
        void ordersFields() {
            InvoiceData data = InvoiceData.of(RecognizedText.EMPTY,
                    List.of(ExtractedField.of(InvoiceFields.TOTAL_AMOUNT, "1"),
                            ExtractedField.of(InvoiceFields.SUPPLIER, "2")));

            assertEquals(List.of(InvoiceFields.SUPPLIER, InvoiceFields.TOTAL_AMOUNT),
                    data.fields().stream().map(ExtractedField::definition).toList());
        }

        @Test
        @DisplayName("the table can be replaced without disturbing the fields")
        void replacesTheTable() {
            InvoiceData replaced = sample().withLineItems(List.of());

            assertFalse(replaced.hasLineItems());
            assertEquals(2, replaced.recognizedCount());
        }
    }

    @Nested
    @DisplayName("A row of the goods table")
    class Rows {

        @Test
        @DisplayName("absent columns are reported absent, not as zero")
        void keepsAbsentColumnsAbsent() {
            LineItem item = LineItem.of("Manopera", null, "", "1100.00");

            assertTrue(item.quantity().isEmpty());
            assertTrue(item.unitPrice().isEmpty());
            assertEquals("N/A", item.quantityOr("N/A"));
        }

        @Test
        @DisplayName("a row without a description is not a row")
        void demandsADescription() {
            assertThrows(IllegalArgumentException.class,
                    () -> LineItem.of("   ", "1", "1.00", "1.00"));
        }
    }
}
