package com.invoiceocr.extraction.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.LineItem;
import com.invoiceocr.extraction.DocumentRegions;
import com.invoiceocr.extraction.text.SearchText;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reading the goods table")
class TableLineItemExtractorTest {

    private final TableLineItemExtractor extractor = new TableLineItemExtractor();

    private List<LineItem> read(String page) {
        SearchText text = SearchText.of(page);
        return extractor.extract(text, DocumentRegions.items(text));
    }

    @Test
    @DisplayName("reads description, quantity, unit price and value from a full row")
    void readsAFullRow() {
        List<LineItem> items = read("""
                Denumire                Cant    Pret      Valoare
                Transport marfa            1   840,00      840,00

                Total fara TVA                             840,00
                """);

        assertEquals(1, items.size());
        LineItem item = items.get(0);
        assertEquals("Transport marfa", item.description());
        assertEquals(Optional.of("1"), item.quantity());
        assertEquals(Optional.of("840.00"), item.unitPrice());
        assertEquals("840.00", item.lineTotal());
    }

    @Test
    @DisplayName("a row with only a value keeps the value and reports the rest as absent")
    void readsAValueOnlyRow() {
        List<LineItem> items = read("""
                Produs                                  Valoare
                Ambalaje carton                       12.400,00

                Total fara TVA                        12.400,00
                """);

        assertEquals(1, items.size());
        assertEquals("12400.00", items.get(0).lineTotal());
        assertTrue(items.get(0).quantity().isEmpty());
        assertTrue(items.get(0).unitPrice().isEmpty());
    }

    @Test
    @DisplayName("a leading row number is dropped rather than swallowing the description")
    void skipsTheRowIndexColumn() {
        List<LineItem> items = read("""
                Nr. crt  Denumire produs        Cant     Pret      Valoare
                1        Ciment Portland          10    32,00       320,00
                2        Nisip spalat sac         25     8,00       200,00

                Total fara TVA                                      520,00
                """);

        assertEquals(List.of("Ciment Portland", "Nisip spalat sac"),
                items.stream().map(LineItem::description).toList());
        assertEquals(Optional.of("10"), items.get(0).quantity());
        assertEquals("320.00", items.get(0).lineTotal());
    }

    @Test
    @DisplayName("two figures are read as quantity and value when the first is whole")
    void readsAQuantityAndValue() {
        List<LineItem> items = read("""
                Denumire                Cant     Valoare
                Analize                    2      450,00

                Total fara TVA                    450,00
                """);

        assertEquals(Optional.of("2"), items.get(0).quantity());
        assertTrue(items.get(0).unitPrice().isEmpty());
        assertEquals("450.00", items.get(0).lineTotal());
    }

    @Test
    @DisplayName("two figures are read as unit price and value when the first has decimals")
    void readsAUnitPriceAndValue() {
        List<LineItem> items = read("""
                Denumire                Pret     Valoare
                Consultanta            30,50      450,00

                Total fara TVA                    450,00
                """);

        assertEquals(Optional.of("30.50"), items.get(0).unitPrice());
        assertTrue(items.get(0).quantity().isEmpty());
    }

    @Test
    @DisplayName("rules, blank lines and summary rows are not items")
    void skipsWhatIsNotAnItem() {
        List<LineItem> items = read("""
                Produs                                  Valoare
                --------------------------------------------------
                Manopera                               1.100,00
                --------------------------------------------------
                Subtotal                               1.100,00

                Total fara TVA                         1.100,00
                """);

        assertEquals(List.of("Manopera"), items.stream().map(LineItem::description).toList());
    }

    @Test
    @DisplayName("a page with no heading row yields no table rather than a guessed one")
    void needsAHeadingRow() {
        assertTrue(read("""
                Furnizor: SC ALFA SRL
                Ceva 100,00
                Altceva 200,00
                """).isEmpty());
    }

    @Test
    @DisplayName("a line with no figures on it is not a row")
    void ignoresProse() {
        List<LineItem> items = read("""
                Denumire                                Valoare
                Va multumim pentru colaborare
                Manopera                               1.100,00

                Total fara TVA                         1.100,00
                """);

        assertEquals(List.of("Manopera"), items.stream().map(LineItem::description).toList());
    }
}
