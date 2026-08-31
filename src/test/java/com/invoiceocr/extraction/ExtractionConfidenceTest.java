package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What each strategy says a value is worth, and why the numbers have to differ.
 *
 * <p>Nothing chooses between two readings of the same page unless the strategies
 * behind them are rated apart, so these ratings are as much a part of the
 * contract as the values themselves.</p>
 */
@DisplayName("How much a find is worth")
class ExtractionConfidenceTest {

    private static Extraction extract(FieldExtractor extractor, String page) {
        return extractor.extract(SearchText.of(page)).orElseThrow();
    }

    @Test
    @DisplayName("a value beside its label is worth more than one under it")
    void ratesBesideAboveBelow() {
        Extraction beside = extract(
                LabelledValueExtractor.sameLine("Data facturii" + ValuePatterns.SEPARATOR,
                        ValuePatterns.date()),
                "Data facturii: 05.03.2024\n");
        Extraction below = extract(
                LabelledValueExtractor.within("Data emiterii" + ValuePatterns.SEPARATOR,
                        ValuePatterns.date(), 2),
                "Data emiterii\n05.03.2024\n");

        assertEquals(FieldConfidence.LABELLED, beside.confidence());
        assertEquals(FieldConfidence.NEARBY, below.confidence());
        assertTrue(beside.confidence() > below.confidence());
    }

    @Test
    @DisplayName("a shape match is worth less than a labelled one, and a guess less again")
    void ratesShapesBelowLabels() {
        Extraction shaped = extract(
                ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()), "RO 2468106\n");
        Extraction guessed = extract(
                ValueShapeExtractor.largest(ValuePatterns.amount()), "100,00\n200,00\n");

        assertEquals(FieldConfidence.LOOSE, shaped.confidence());
        assertEquals(FieldConfidence.INFERRED, guessed.confidence());
        assertTrue(FieldConfidence.LABELLED > shaped.confidence());
        assertTrue(shaped.confidence() > guessed.confidence());
    }

    @Test
    @DisplayName("finding a value in the right block is worth more than finding it anywhere")
    void ratesTheRightBlockHigher() {
        String page = """
                CUMPARATOR: SC OMEGA RETAIL SRL
                CUI: RO 5550005

                FURNIZOR: SC EPSILON SERVICE SRL
                CUI: RO 6660006
                """;
        Extraction anywhere = extract(
                ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()), page);
        Extraction scoped = extract(RegionScopedExtractor.inSupplierBlock(
                ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode())), page);

        assertTrue(scoped.confidence() > anywhere.confidence());
        assertTrue(scoped.strategy().startsWith("supplier-block/"), scoped.strategy());
    }

    @Test
    @DisplayName("the scope bonus never lifts a guess to the level of a labelled read")
    void capsTheScopeBonus() {
        Extraction scoped = extract(RegionScopedExtractor.inSupplierBlock(
                ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode())), "RO 2468106\n");

        assertTrue(scoped.confidence() <= FieldConfidence.LABELLED);
    }

    @Test
    @DisplayName("each further rung of a ladder discounts what it finds")
    void discountsLaterRungs() {
        FirstMatchFieldExtractor ladder = new FirstMatchFieldExtractor(List.of(
                LabelledValueExtractor.sameLine("Total de plata" + ValuePatterns.SEPARATOR,
                        ValuePatterns.amount()),
                LabelledValueExtractor.sameLine("Total" + ValuePatterns.SEPARATOR,
                        ValuePatterns.amount())));

        Extraction first = extract(ladder, "Total de plata 1.190,00\n");
        Extraction second = extract(ladder, "Total 1.000,00\n");

        assertEquals(FieldConfidence.LABELLED, first.confidence());
        assertTrue(second.confidence() < first.confidence(),
                "the same strategy on a later rung is a weaker claim");
    }

    @Test
    @DisplayName("a ladder can be asked for every answer it has, not only its first")
    void offersAlternatives() {
        FirstMatchFieldExtractor ladder = new FirstMatchFieldExtractor(List.of(
                ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode()),
                ValueShapeExtractor.last(ValuePatterns.prefixedFiscalCode())));

        SearchText page = SearchText.of("CUI RO 5550005\nCUI RO 6660006\n");
        List<Extraction> all = ladder.alternatives(page, page.whole());

        assertEquals(2, all.size());
        assertEquals("RO 5550005", all.get(0).value());
        assertEquals("RO 6660006", all.get(1).value());
    }

    @Test
    @DisplayName("a plain extractor offers exactly one answer")
    void aPlainExtractorHasOneAnswer() {
        SearchText page = SearchText.of("RO 2468106\n");
        FieldExtractor plain = ValueShapeExtractor.first(ValuePatterns.prefixedFiscalCode());

        assertEquals(1, plain.alternatives(page, page.whole()).size());
        assertEquals(Optional.of("RO 2468106"), plain.extractValue(page));
    }

    @Test
    @DisplayName("a decorator says so in the strategy it reports")
    void namesTheDecorator() {
        Extraction gated = extract(new ContextGatedExtractor(
                        ValueShapeExtractor.largest(ValuePatterns.amount()), "total"),
                "Total\n100,00\n200,00\n");

        assertEquals("gated/shape-largest", gated.strategy());
    }
}
