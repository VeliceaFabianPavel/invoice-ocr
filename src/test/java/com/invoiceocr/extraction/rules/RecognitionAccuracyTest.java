package com.invoiceocr.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.LineItem;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
import com.invoiceocr.extraction.items.LineItemRefinement;
import com.invoiceocr.extraction.items.TableLineItemExtractor;
import com.invoiceocr.extraction.validation.ArithmeticRefinement;
import com.invoiceocr.extraction.validation.DateRefinement;
import com.invoiceocr.support.InvoiceCorpus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Scores the rules against a corpus of real invoice layouts.
 *
 * <p>This is the test that keeps the recogniser honest. Any single rule can be
 * made to pass by writing a pattern for it; what matters is that the whole
 * ladder of strategies reads every layout at once, and that a change made for
 * one invoice does not quietly break another.</p>
 *
 * <p>The corpus has been the measure of every version. In 1.1.0 it scored 38 of
 * 60 with ten fields reported as {@code N/A}; the layered strategies took that
 * to 60 of 60. In 1.2.0 it grew to eighteen layouts and twelve fields, and the
 * bar is the same: everything, on every sample, or the build fails.</p>
 */
@DisplayName("Recognition accuracy")
class RecognitionAccuracyTest {

    private final InvoiceParser parser = new RuleBasedInvoiceParser(
            new RomanianInvoiceRuleProvider(), refinements());

    /** The pipeline the application wires up, so the corpus measures what ships. */
    private static List<InvoiceRefinement> refinements() {
        return List.of(new LineItemRefinement(new TableLineItemExtractor()),
                new DateRefinement(),
                new ArithmeticRefinement());
    }

    static Stream<InvoiceCorpus.Sample> corpus() {
        return InvoiceCorpus.all().stream();
    }

    @DisplayName("every field of every layout is read correctly")
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void readsEveryLayout(InvoiceCorpus.Sample sample) {
        InvoiceData data = parser.parse(RecognizedText.of(sample.text()));

        List<String> failures = new ArrayList<>();
        for (Map.Entry<FieldDefinition, String> expected : sample.expected().entrySet()) {
            String actual = data.field(expected.getKey())
                    .flatMap(ExtractedField::value)
                    .orElse(null);
            if (!expected.getValue().equals(actual)) {
                failures.add(expected.getKey().key() + ": expected '" + expected.getValue()
                        + "' but was " + (actual == null ? "N/A" : "'" + actual + "'"));
            }
        }
        assertTrue(failures.isEmpty(), () -> sample.name() + " ->\n  " + String.join("\n  ", failures));
    }

    @DisplayName("the goods table of every layout is read row for row")
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void readsEveryTable(InvoiceCorpus.Sample sample) {
        InvoiceData data = parser.parse(RecognizedText.of(sample.text()));

        assertEquals(sample.expectedItems(),
                data.lineItems().stream().map(LineItem::description).toList(),
                () -> sample.name() + ": the rows read do not match the rows printed");
    }

    @Test
    @DisplayName("the corpus as a whole is read without a single N/A")
    void leavesNothingUnread() {
        int expected = 0;
        int found = 0;
        for (InvoiceCorpus.Sample sample : InvoiceCorpus.all()) {
            InvoiceData data = parser.parse(RecognizedText.of(sample.text()));
            for (Map.Entry<FieldDefinition, String> entry : sample.expected().entrySet()) {
                expected++;
                if (entry.getValue().equals(data.field(entry.getKey())
                        .flatMap(ExtractedField::value).orElse(null))) {
                    found++;
                }
            }
        }
        assertEquals(expected, found, "the corpus should be read in full");
    }

    @Test
    @DisplayName("a page that is not an invoice is not given invented values")
    void doesNotInventValues() {
        InvoiceData data = parser.parse(RecognizedText.of("|||  ~~~ 8B8B8 ???\nnothing here\n"));

        assertEquals(InvoiceFields.ALL.size(), data.fields().size(),
                "every field is still reported");
        assertEquals(0, data.recognizedCount(),
                "with no invoice vocabulary on the page, the guessing strategies must stay quiet");
    }

    @Test
    @DisplayName("the amounts on every layout satisfy net + VAT = total")
    void everyLayoutBalances() {
        List<String> unbalanced = new ArrayList<>();
        for (InvoiceCorpus.Sample sample : InvoiceCorpus.all()) {
            InvoiceData data = parser.parse(RecognizedText.of(sample.text()));
            java.util.Optional<java.math.BigDecimal> net = amount(data, InvoiceFields.NET_AMOUNT);
            java.util.Optional<java.math.BigDecimal> vat = amount(data, InvoiceFields.VAT_AMOUNT);
            java.util.Optional<java.math.BigDecimal> total = amount(data, InvoiceFields.TOTAL_AMOUNT);
            if (net.isEmpty() || vat.isEmpty() || total.isEmpty()) {
                unbalanced.add(sample.name() + ": an amount is missing");
            } else if (net.get().add(vat.get()).subtract(total.get()).abs()
                    .compareTo(new java.math.BigDecimal("0.02")) > 0) {
                unbalanced.add(sample.name() + ": " + net.get() + " + " + vat.get()
                        + " != " + total.get());
            }
        }
        assertTrue(unbalanced.isEmpty(), () -> String.join("\n  ", unbalanced));
    }

    @Test
    @DisplayName("most values are read outright rather than inferred")
    void readsRatherThanGuesses() {
        long guessed = 0;
        long present = 0;
        for (InvoiceCorpus.Sample sample : InvoiceCorpus.all()) {
            InvoiceData data = parser.parse(RecognizedText.of(sample.text()));
            present += data.recognizedCount();
            guessed += data.needingReview().size();
        }
        long inferred = guessed;
        long found = present;
        assertTrue(inferred * 5 < found,
                () -> "too many values are guesses: " + inferred + " of " + found);
    }

    private static java.util.Optional<java.math.BigDecimal> amount(InvoiceData data,
                                                                   FieldDefinition field) {
        return data.valueOf(field).map(java.math.BigDecimal::new);
    }
}
