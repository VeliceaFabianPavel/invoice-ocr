package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which part of the page is which.
 *
 * <p>Asserted on the text a region covers rather than on its offsets, so the
 * tests say what the region is <em>for</em> and survive an edit to the fixture.</p>
 */
@DisplayName("Parts of the page")
class DocumentRegionsTest {

    private static final SearchText BOTH_PARTIES = SearchText.of("""
            FURNIZOR
            SC ALFA CONSTRUCT SRL
            CUI: RO9876544

            CUMPARATOR
            SC BETA COMERT SRL
            CUI: RO1112223

            Total fara TVA   100,00
            """);

    private static final SearchText BUYER_FIRST = SearchText.of("""
            CUMPARATOR: SC OMEGA RETAIL SRL
            CUI: RO5550005

            FURNIZOR: SC EPSILON SERVICE SRL
            CUI: RO6660006
            """);

    private static String textOf(SearchText text, TextRegion region) {
        return text.slice(region.start(), region.end());
    }

    @Test
    @DisplayName("the supplier's block stops where the buyer's begins")
    void suppliersBlockStopsAtTheBuyer() {
        String block = textOf(BOTH_PARTIES, DocumentRegions.supplier(BOTH_PARTIES));

        assertTrue(block.contains("SC ALFA CONSTRUCT SRL"));
        assertFalse(block.contains("SC BETA COMERT SRL"));
    }

    @Test
    @DisplayName("the buyer's block stops where the page does")
    void buyersBlockRunsToTheEnd() {
        String block = textOf(BOTH_PARTIES, DocumentRegions.buyer(BOTH_PARTIES));

        assertTrue(block.contains("SC BETA COMERT SRL"));
        assertFalse(block.contains("SC ALFA CONSTRUCT SRL"));
    }

    @Test
    @DisplayName("with the buyer printed first, each block still holds its own company")
    void survivesTheReversedOrder() {
        assertTrue(textOf(BUYER_FIRST, DocumentRegions.supplier(BUYER_FIRST))
                .contains("SC EPSILON SERVICE SRL"));
        assertFalse(textOf(BUYER_FIRST, DocumentRegions.supplier(BUYER_FIRST))
                .contains("SC OMEGA RETAIL SRL"));
        assertTrue(textOf(BUYER_FIRST, DocumentRegions.buyer(BUYER_FIRST))
                .contains("SC OMEGA RETAIL SRL"));
        assertFalse(textOf(BUYER_FIRST, DocumentRegions.buyer(BUYER_FIRST))
                .contains("SC EPSILON SERVICE SRL"));
    }

    @Test
    @DisplayName("a page that names no buyer has no buyer block, rather than the supplier's")
    void noBuyerMeansNoBuyerBlock() {
        SearchText letterhead = SearchText.of("SC SIGMA MEDICAL SRL\nRO 2468106\n");

        assertTrue(DocumentRegions.buyer(letterhead).isEmpty());
    }

    @Test
    @DisplayName("an unlabelled page is all supplier, since a letterhead usually is")
    void anUnlabelledPageIsAllSupplier() {
        SearchText letterhead = SearchText.of("SC SIGMA MEDICAL SRL\nRO 2468106\n");

        assertEquals(letterhead.whole(), DocumentRegions.supplier(letterhead));
    }

    @Test
    @DisplayName("the totals block starts at the first summary line")
    void findsTheTotalsBlock() {
        String block = textOf(BOTH_PARTIES, DocumentRegions.totals(BOTH_PARTIES));

        assertTrue(block.startsWith("Total fara TVA"));
    }

    @Test
    @DisplayName("a page with no summary line is treated as all of it, not none")
    void survivesAPageWithNoTotals() {
        SearchText plain = SearchText.of("Furnizor: SC ALFA SRL\n");

        assertEquals(plain.whole(), DocumentRegions.totals(plain));
    }

    @Test
    @DisplayName("the goods table runs from under its headings to the summary block")
    void findsTheItemsBlock() {
        SearchText page = SearchText.of("""
                Denumire            Cant     Valoare
                Ciment                10      320,00
                Nisip                 25      200,00

                Total fara TVA                520,00
                """);
        String block = textOf(page, DocumentRegions.items(page));

        assertTrue(block.contains("Ciment"));
        assertTrue(block.contains("Nisip"));
        assertFalse(block.contains("Denumire"), "the heading row is not an item");
        assertFalse(block.contains("Total fara TVA"), "nor is the summary");
    }

    @Test
    @DisplayName("no heading row means no table, rather than a table guessed at")
    void noHeadingMeansNoTable() {
        SearchText page = SearchText.of("Ceva 100,00\nAltceva 200,00\n");

        assertTrue(DocumentRegions.items(page).isEmpty());
    }
}
