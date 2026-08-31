package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.normalization.Normalizers;
import com.invoiceocr.extraction.text.ValuePatterns;
import com.invoiceocr.extraction.validation.ValueCheck;
import com.invoiceocr.extraction.validation.Verdict;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the parser chooses between the answers a ladder gives it.
 *
 * <p>Driven with rules built here rather than the Romanian rule set, so each
 * test states one rule of the choosing and nothing else can influence it.</p>
 */
@DisplayName("Choosing between the answers a rule gives")
class RuleBasedInvoiceParserSelectionTest {

    private static final String PAGE = "CUI: RO 5550005\nCUI: RO 6660006\n";

    /** A provider of exactly the rules a test hands it. */
    private static ExtractionRuleProvider rules(ExtractionRule... all) {
        return () -> List.of(all);
    }

    private static InvoiceData parse(String page, ExtractionRule... all) {
        return new RuleBasedInvoiceParser(rules(all)).parse(RecognizedText.of(page));
    }

    private static ExtractionRule fiscalCodeRule(ValueCheck check) {
        return ExtractionRule.forField(InvoiceFields.FISCAL_CODE)
                .using(ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()))
                .using(ValueShapeExtractor.last(ValuePatterns.prefixedFiscalCode()))
                .normalizedBy(Normalizers.fiscalCode())
                .checkedBy(check)
                .build();
    }

    @Test
    @DisplayName("with nothing to check, the first rung wins as it always did")
    void orderDecidesWhenNothingIsChecked() {
        InvoiceData data = parse(PAGE, fiscalCodeRule(ValueCheck.none()));

        assertEquals(Optional.of("RO5550005"), data.valueOf(InvoiceFields.FISCAL_CODE));
    }

    @Test
    @DisplayName("a proven later rung overrules an unproven earlier one")
    void proofOverrulesOrder() {
        ValueCheck onlyTheSecond = value ->
                "RO6660006".equals(value) ? Verdict.PROVEN : Verdict.UNPROVEN;

        InvoiceData data = parse(PAGE, fiscalCodeRule(onlyTheSecond));

        assertEquals(Optional.of("RO6660006"), data.valueOf(InvoiceFields.FISCAL_CODE));
        assertEquals(FieldConfidence.VERIFIED,
                data.field(InvoiceFields.FISCAL_CODE).orElseThrow().confidence());
    }

    @Test
    @DisplayName("a proven earlier rung is not displaced by a proven later one")
    void thefirstProofWins() {
        InvoiceData data = parse(PAGE, fiscalCodeRule(value -> Verdict.PROVEN));

        assertEquals(Optional.of("RO5550005"), data.valueOf(InvoiceFields.FISCAL_CODE));
    }

    @Test
    @DisplayName("a doubtful value is kept when nothing better exists, and flagged")
    void keepsADoubtfulValueAsALastResort() {
        InvoiceData data = parse(PAGE, fiscalCodeRule(value -> Verdict.DOUBTFUL));

        ExtractedField code = data.field(InvoiceFields.FISCAL_CODE).orElseThrow();
        assertEquals(Optional.of("RO5550005"), code.value());
        assertTrue(code.needsReview(), "the user should be told this one did not check out");
        assertTrue(code.strategy().endsWith("+unchecked"), code.strategy());
    }

    @Test
    @DisplayName("an unproven value is preferred to a doubtful one")
    void prefersUnprovenToDoubtful() {
        ValueCheck doubtsTheFirst = value ->
                "RO5550005".equals(value) ? Verdict.DOUBTFUL : Verdict.UNPROVEN;

        InvoiceData data = parse(PAGE, fiscalCodeRule(doubtsTheFirst));

        assertEquals(Optional.of("RO6660006"), data.valueOf(InvoiceFields.FISCAL_CODE));
    }

    @Test
    @DisplayName("an impossible value is never reported, however little else there is")
    void neverReportsAnImpossibleValue() {
        InvoiceData data = parse(PAGE, fiscalCodeRule(value -> Verdict.IMPOSSIBLE));

        assertTrue(data.valueOf(InvoiceFields.FISCAL_CODE).isEmpty());
    }

    @Test
    @DisplayName("several checks all apply, and the worst verdict decides")
    void combinesChecks() {
        ExtractionRule rule = ExtractionRule.forField(InvoiceFields.FISCAL_CODE)
                .using(ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()))
                .normalizedBy(Normalizers.fiscalCode())
                .checkedBy(value -> Verdict.PROVEN)
                .checkedBy(value -> Verdict.IMPOSSIBLE)
                .build();

        assertTrue(parse(PAGE, rule).valueOf(InvoiceFields.FISCAL_CODE).isEmpty());
    }

    @Test
    @DisplayName("the refinements run in the order they were given")
    void runsRefinementsInOrder() {
        InvoiceRefinement first = (data, text) -> Refinements.replace(data,
                ExtractedField.of(InvoiceFields.CURRENCY, "EUR", FieldConfidence.DERIVED, "first"));
        InvoiceRefinement second = (data, text) -> Refinements.replace(data,
                ExtractedField.of(InvoiceFields.CURRENCY, "RON", FieldConfidence.DERIVED, "second"));

        InvoiceData data = new RuleBasedInvoiceParser(rules(fiscalCodeRule(ValueCheck.none())),
                List.of(first, second)).parse(RecognizedText.of(PAGE));

        assertEquals(Optional.of("RON"), data.valueOf(InvoiceFields.CURRENCY));
    }

    @Test
    @DisplayName("a rule with no extractor is a mistake caught at assembly, not at runtime")
    void refusesARuleWithNoStrategy() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                        () -> ExtractionRule.forField(InvoiceFields.SUPPLIER).build())
                .getMessage().contains("supplier"));
    }
}
