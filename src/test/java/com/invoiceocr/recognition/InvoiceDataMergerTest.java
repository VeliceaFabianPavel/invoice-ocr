package com.invoiceocr.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.image.ImagePreprocessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Merging several readings of one page")
class InvoiceDataMergerTest {

    private final InvoiceDataMerger merger = new InvoiceDataMerger();

    // ------------------------------------------------------------ fixtures

    private static PassOutcome pass(String name, String rawText, ExtractedField... fields) {
        return pass(name, rawText, List.of(), fields);
    }

    private static PassOutcome pass(String name, String rawText, List<LineItem> items,
                                    ExtractedField... fields) {
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
        InvoiceData data = InvoiceData.of(RecognizedText.of(rawText), all, items);
        return new PassOutcome(
                RecognitionPass.of(name, ImagePreprocessor.identity()), data, 1L);
    }

    private static ExtractedField field(FieldDefinition definition, String value,
                                        double confidence, String strategy) {
        return ExtractedField.of(definition, value, confidence, strategy);
    }

    // --------------------------------------------------------------- tests

    @Test
    @DisplayName("a single reading is returned as it is")
    void passesOneOutcomeThrough() {
        PassOutcome only = pass("plain", "text",
                field(InvoiceFields.SUPPLIER, "SC ALFA SRL", FieldConfidence.LABELLED, "labelled"));

        assertSame(only.data(), merger.merge(List.of(only)));
    }

    @Test
    @DisplayName("the better-found value wins when the passes disagree")
    void prefersTheBetterStrategy() {
        InvoiceData merged = merger.merge(List.of(
                pass("plain", "a", field(InvoiceFields.TOTAL_AMOUNT, "999.00",
                        FieldConfidence.INFERRED, "shape-largest")),
                pass("binarised", "b", field(InvoiceFields.TOTAL_AMOUNT, "1190.00",
                        FieldConfidence.LABELLED, "labelled"))));

        assertEquals(Optional.of("1190.00"), merged.valueOf(InvoiceFields.TOTAL_AMOUNT));
    }

    @Test
    @DisplayName("two passes agreeing outrank one pass that is more sure on its own")
    void rewardsAgreement() {
        InvoiceData merged = merger.merge(List.of(
                pass("plain", "a", field(InvoiceFields.INVOICE_NUMBER, "ZT-0091",
                        FieldConfidence.NEARBY, "labelled-below")),
                pass("straightened", "b", field(InvoiceFields.INVOICE_NUMBER, "ZT-0091",
                        FieldConfidence.NEARBY, "labelled-below")),
                pass("binarised", "c", field(InvoiceFields.INVOICE_NUMBER, "ZT-009l",
                        FieldConfidence.LABELLED, "labelled"))));

        assertEquals(Optional.of("ZT-0091"), merged.valueOf(InvoiceFields.INVOICE_NUMBER),
                "the same string from two renderings is stronger evidence than one confident read");
    }

    @Test
    @DisplayName("agreement raises the confidence and says so in the strategy")
    void recordsTheAgreement() {
        InvoiceData merged = merger.merge(List.of(
                pass("plain", "a", field(InvoiceFields.SUPPLIER, "SC ALFA SRL",
                        FieldConfidence.SHAPED, "company-line")),
                pass("binarised", "b", field(InvoiceFields.SUPPLIER, "SC ALFA SRL",
                        FieldConfidence.SHAPED, "company-line"))));

        ExtractedField supplier = merged.field(InvoiceFields.SUPPLIER).orElseThrow();
        assertTrue(supplier.confidence() > FieldConfidence.SHAPED);
        assertTrue(supplier.strategy().endsWith("+agreed2"), supplier.strategy());
    }

    @Test
    @DisplayName("a field only one pass found is kept, not outvoted by silence")
    void silenceIsNotAVote() {
        InvoiceData merged = merger.merge(List.of(
                pass("plain", "a"),
                pass("straightened", "b"),
                pass("binarised", "c", field(InvoiceFields.IBAN, "RO49 AAAA 1B31 0075 9384 0000",
                        FieldConfidence.VERIFIED, "labelled+checked"))));

        assertEquals(Optional.of("RO49 AAAA 1B31 0075 9384 0000"), merged.valueOf(InvoiceFields.IBAN));
    }

    @Test
    @DisplayName("the raw text shown is the one from the pass that read the most")
    void keepsTheBestTranscription() {
        InvoiceData merged = merger.merge(List.of(
                pass("plain", "poor transcription"),
                pass("binarised", "good transcription",
                        field(InvoiceFields.SUPPLIER, "SC ALFA SRL", FieldConfidence.LABELLED, "labelled"),
                        field(InvoiceFields.TOTAL_AMOUNT, "100.00", FieldConfidence.LABELLED, "labelled"))));

        assertEquals("good transcription", merged.source().value());
    }

    @Test
    @DisplayName("the table is taken whole from the pass that read the most rows")
    void takesTheRichestTable() {
        LineItem one = LineItem.of("Ciment", null, null, "320.00");
        LineItem two = LineItem.of("Nisip", null, null, "200.00");

        InvoiceData merged = merger.merge(List.of(
                pass("plain", "a", List.of(one)),
                pass("binarised", "b", List.of(one, two))));

        assertEquals(2, merged.lineItems().size());
    }

    @Test
    @DisplayName("the refinements run again over the merged fields")
    void refinesTheMergedResult() {
        InvoiceRefinement stamp = (data, text) -> com.invoiceocr.extraction.Refinements.replace(data,
                ExtractedField.of(InvoiceFields.CURRENCY, "RON", FieldConfidence.DERIVED, "stamped"));

        InvoiceData merged = new InvoiceDataMerger(List.of(stamp)).merge(List.of(
                pass("plain", "a", field(InvoiceFields.TOTAL_AMOUNT, "100.00",
                        FieldConfidence.LABELLED, "labelled")),
                pass("binarised", "b")));

        assertEquals(Optional.of("RON"), merged.valueOf(InvoiceFields.CURRENCY));
    }

    @Test
    @DisplayName("merging nothing is a programming error, not an empty invoice")
    void refusesAnEmptyMerge() {
        assertThrows(IllegalArgumentException.class, () -> merger.merge(List.of()));
    }
}
