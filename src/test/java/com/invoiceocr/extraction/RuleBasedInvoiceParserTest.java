package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.normalization.Normalizers;
import com.invoiceocr.extraction.normalization.ValueNormalizer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleBasedInvoiceParser")
class RuleBasedInvoiceParserTest {

    private static final FieldDefinition ALPHA = new FieldDefinition("alpha", "field.alpha", 10);
    private static final FieldDefinition BETA = new FieldDefinition("beta", "field.beta", 20);

    @Test
    @DisplayName("reports a field with no match as present but empty")
    void keepsMissingFields() {
        InvoiceParser parser = parserFor(rule(ALPHA, "Alpha:\\s*(\\w+)"), rule(BETA, "Beta:\\s*(\\w+)"));

        InvoiceData data = parser.parse(RecognizedText.of("Alpha: one"));

        assertEquals(2, data.fields().size());
        assertEquals(Optional.of("one"), data.valueOf(ALPHA));
        assertTrue(data.valueOf(BETA).isEmpty());
        assertEquals(1, data.recognizedCount());
    }

    @Test
    @DisplayName("keeps the first rule that produces a value for a field")
    void firstWinningRuleWins() {
        InvoiceParser parser = parserFor(
                rule(ALPHA, "Missing:\\s*(\\w+)"),
                rule(ALPHA, "Alpha:\\s*(\\w+)"),
                rule(ALPHA, "Alpha:\\s*(\\w)"));

        assertEquals(Optional.of("one"), parser.parse(RecognizedText.of("Alpha: one")).valueOf(ALPHA));
    }

    @Test
    @DisplayName("orders fields for display, not by rule declaration order")
    void ordersFieldsForDisplay() {
        InvoiceParser parser = parserFor(rule(BETA, "Beta:\\s*(\\w+)"), rule(ALPHA, "Alpha:\\s*(\\w+)"));

        List<String> keys = parser.parse(RecognizedText.of("Beta: two\nAlpha: one"))
                .fields().stream().map(field -> field.definition().key()).toList();

        assertEquals(List.of("alpha", "beta"), keys);
    }

    @Test
    @DisplayName("discards a capture that normalises to nothing")
    void discardsBlankNormalisedValues() {
        ExtractionRule blanking = ExtractionRule.forField(ALPHA)
                .matching("Alpha:\\s*(\\.+)")
                .normalizedBy(Normalizers.text())
                .build();

        assertFalse(parserFor(blanking).parse(RecognizedText.of("Alpha: ...")).valueOf(ALPHA).isPresent());
    }

    private static InvoiceParser parserFor(ExtractionRule... rules) {
        List<ExtractionRule> ruleList = List.of(rules);
        return new RuleBasedInvoiceParser(() -> ruleList);
    }

    private static ExtractionRule rule(FieldDefinition field, String regex) {
        return ExtractionRule.forField(field)
                .matching(regex)
                .normalizedBy(ValueNormalizer.identity())
                .build();
    }
}
