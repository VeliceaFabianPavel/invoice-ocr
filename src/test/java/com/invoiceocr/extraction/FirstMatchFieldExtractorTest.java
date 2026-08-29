package com.invoiceocr.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.extraction.text.SearchText;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FirstMatchFieldExtractor")
class FirstMatchFieldExtractorTest {

    private static final SearchText TEXT = SearchText.of("""
            Total 100,00
            Total de plata 119,00
            """);

    @Test
    @DisplayName("prefers the earlier candidate even when it matches later in the text")
    void ordersByConfidenceNotByPosition() {
        FieldExtractor extractor = FirstMatchFieldExtractor.ofPatterns(
                "Total de plata\\s*([0-9,.]+)",
                "Total\\s*([0-9,.]+)");

        assertEquals(Optional.of("119,00"), extractor.extract(TEXT));
    }

    @Test
    @DisplayName("a single alternation regex would instead take whichever label comes first")
    void demonstratesWhyOrderingMatters() {
        FieldExtractor naive = RegexFieldExtractor.of("(?:Total de plata|Total)\\s*([0-9,.]+)");

        assertEquals(Optional.of("100,00"), naive.extract(TEXT));
    }

    @Test
    @DisplayName("returns empty when no candidate matches")
    void returnsEmptyWithoutMatch() {
        FieldExtractor extractor = FirstMatchFieldExtractor.ofPatterns("Discount\\s*([0-9]+)");

        assertTrue(extractor.extract(TEXT).isEmpty());
    }
}
