package com.invoiceocr.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
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
 * one invoice does not quietly break another. Before the strategies were
 * layered this corpus scored 38 of 60 with 10 fields reported as N/A.</p>
 */
@DisplayName("Recognition accuracy")
class RecognitionAccuracyTest {

    private final InvoiceParser parser = new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider());

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

        assertEquals(6, data.fields().size(), "all six fields are still reported");
        assertEquals(0, data.recognizedCount(),
                "with no invoice vocabulary on the page, the guessing strategies must stay quiet");
    }
}
