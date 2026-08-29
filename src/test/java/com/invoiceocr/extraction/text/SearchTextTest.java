package com.invoiceocr.extraction.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Prepared page text")
class SearchTextTest {

    @Nested
    @DisplayName("Diacritic folding")
    class Folding {

        @Test
        @DisplayName("keeps the length identical, so offsets still line up")
        void preservesLength() {
            String accented = "Vânzător: SC LAMBDA SOLUȚII SRL";
            assertEquals(accented.length(), TextFolding.fold(accented).length());
        }

        @Test
        @DisplayName("a plain ASCII pattern matches accented text")
        void foldsRomanianLetters() {
            assertEquals("Vanzator: Total de plata, fara TVA",
                    TextFolding.fold("Vânzător: Total de plată, fără TVA"));
        }

        @Test
        @DisplayName("the value handed back keeps its accents")
        void returnsTheOriginalSpelling() {
            SearchText text = SearchText.of("Vânzător: SC LAMBDA SOLUȚII SRL");

            Optional<ValuePattern.Found> found =
                    ValuePattern.of("Vanzator:\\s*(.+)").firstIn(text, text.whole());

            assertEquals(Optional.of("SC LAMBDA SOLUȚII SRL"), found.map(ValuePattern.Found::value));
        }
    }

    @Nested
    @DisplayName("Line navigation")
    class Lines {

        private final SearchText text = SearchText.of("first\nsecond\nthird\nfourth");

        @Test
        @DisplayName("finds the end of the line an offset sits on")
        void findsLineEnd() {
            assertEquals(5, text.endOfLine(2));
        }

        @Test
        @DisplayName("finds the start of the line an offset sits on")
        void findsLineStart() {
            assertEquals(6, text.startOfLine(8));
        }

        @Test
        @DisplayName("walks forward a fixed number of lines, stopping at the end")
        void skipsLines() {
            assertEquals("third\nfourth", text.original().substring(text.skipLines(0, 2)));
            assertEquals(text.length(), text.skipLines(0, 99));
        }
    }

    @Nested
    @DisplayName("Regions")
    class Regions {

        @Test
        @DisplayName("a search is confined to the region it is given")
        void confinesTheSearch() {
            SearchText text = SearchText.of("RO111111\nRO222222\n");
            TextRegion secondLine = new TextRegion(9, text.length());

            assertEquals(Optional.of("RO222222"),
                    ValuePatterns.prefixedFiscalCode().firstIn(text, secondLine)
                            .map(ValuePattern.Found::value));
        }

        @Test
        @DisplayName("a look-behind can still see just outside the region")
        void keepsBoundsTransparent() {
            SearchText text = SearchText.of("fara TVA 100,00");
            TextRegion afterFara = new TextRegion(5, text.length());

            assertTrue(ValuePattern.of("(?<!fara )(TVA)").firstIn(text, afterFara).isEmpty(),
                    "the guard must see the word that precedes the region");
        }
    }
}
