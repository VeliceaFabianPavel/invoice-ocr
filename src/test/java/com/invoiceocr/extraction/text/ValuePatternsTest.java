package com.invoiceocr.extraction.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Value patterns")
class ValuePatternsTest {

    private static Optional<String> first(ValuePattern pattern, String text) {
        SearchText prepared = SearchText.of(text);
        return pattern.firstIn(prepared, prepared.whole()).map(ValuePattern.Found::value);
    }

    @Nested
    @DisplayName("Amounts")
    class Amounts {

        @DisplayName("reads every thousands and decimal convention")
        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'Total 1.190,00', '1.190,00'",
                "'Total 1,190.00', '1,190.00'",
                "'Total 1 190,00', '1 190,00'",
                "'Total 1071,00', '1071,00'",
                "'Total 840,00', '840,00'",
                "'Total 16.065,00', '16.065,00'"
        })
        void readsAmounts(String line, String expected) {
            assertEquals(Optional.of(expected), first(ValuePatterns.amount(), line));
        }

        @Test
        @DisplayName("survives OCR damage, because a rejected amount is a lost field")
        void survivesOcrDamage() {
            assertEquals(Optional.of("l.428,OO"), first(ValuePatterns.amount(), "Total de plata l.428,OO"));
        }

        @Test
        @DisplayName("does not mistake a date for an amount")
        void ignoresDates() {
            assertEquals(Optional.empty(), first(ValuePatterns.amount(), "Data facturii: 05.03.2024"));
        }

        @Test
        @DisplayName("does not mistake a VAT rate for the VAT amount")
        void ignoresPercentages() {
            assertEquals(Optional.of("190,00"), first(ValuePatterns.amount(), "Total TVA 19% 190,00"));
        }

        @Test
        @DisplayName("does not run two columns together")
        void staysWithinOneNumber() {
            List<String> found = ValuePatterns.amount()
                    .allIn(SearchText.of("840,00      840,00"), SearchText.of("840,00      840,00").whole())
                    .stream().map(ValuePattern.Found::value).toList();
            assertEquals(List.of("840,00", "840,00"), found);
        }

        @Test
        @DisplayName("a word made of confusable letters is not a number")
        void requiresARealDigit() {
            assertEquals(Optional.empty(), first(ValuePatterns.amount(), "SOS OBS"));
        }
    }

    @Nested
    @DisplayName("Dates")
    class Dates {

        @DisplayName("accepts the separators invoices use")
        @ParameterizedTest
        @ValueSource(strings = { "05.03.2024", "5/3/2024", "05-03-24", "O9.O9.2O24" })
        void readsDates(String printed) {
            assertEquals(Optional.of(printed), first(ValuePatterns.date(), "Data " + printed));
        }

        @Test
        @DisplayName("skips an impossible date and keeps looking")
        void skipsImpossibleDates() {
            assertEquals(Optional.of("14.12.2024"),
                    first(ValuePatterns.date(), "Data emiterii 14.14.2024 Data facturii 14.12.2024"));
        }

        @Test
        @DisplayName("validates after repairing OCR digits")
        void validatesRepairedDates() {
            assertTrue(ValuePatterns.isPlausibleDate("O9.O9.2O24"));
            assertFalse(ValuePatterns.isPlausibleDate("32.01.2024"));
            assertFalse(ValuePatterns.isPlausibleDate("05.13.2024"));
        }
    }

    @Nested
    @DisplayName("Fiscal codes")
    class FiscalCodes {

        @Test
        @DisplayName("finds a prefixed code anywhere, which is what saves a letterhead invoice")
        void findsPrefixedCode() {
            assertEquals(Optional.of("RO 2468101"),
                    first(ValuePatterns.prefixedFiscalCode(), "Str. Sanatatii 8\nRO 2468101\nTel 0256"));
        }

        @Test
        @DisplayName("accepts a code damaged by OCR")
        void acceptsDamagedCode() {
            assertEquals(Optional.of("RO 33O44SS"),
                    first(ValuePatterns.prefixedFiscalCode(), "C.U.I.: RO 33O44SS"));
        }

        @Test
        @DisplayName("a bare code needs two real digits, so a stray word cannot pass")
        void bareCodeNeedsDigits() {
            assertEquals(Optional.empty(), first(ValuePatterns.bareFiscalCode(), "SOS"));
        }
    }

    @Nested
    @DisplayName("Document numbers")
    class DocumentNumbers {

        @DisplayName("reads the shapes invoice numbers come in")
        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'Factura nr. FCT-2024/0182', 'FCT-2024/0182'",
                "'Nr. factura GML-7781', 'GML-7781'",
                "'FACTURA NR TH 5567', 'TH 5567'",
                "'Factura nr. 100234 din 08.08.2024', '100234'",
                "'Factura nr. ZT-OO91', 'ZT-OO91'"
        })
        void readsNumbers(String line, String expected) {
            assertEquals(Optional.of(expected), first(ValuePatterns.documentNumber(), line));
        }

        @Test
        @DisplayName("never reports a date as the invoice number")
        void rejectsDates() {
            assertEquals(Optional.empty(), first(ValuePatterns.documentNumber(), "din 08.08.2024"));
        }

        @Test
        @DisplayName("joins a series printed apart from its number")
        void joinsSeriesAndNumber() {
            assertEquals(Optional.of("ALF 00420"),
                    first(ValuePatterns.seriesAndNumber(), "Seria ALF nr. 00420"));
        }

        @Test
        @DisplayName("'Seria si numarul' is a label, not a series and a number")
        void ignoresTheLabelItself() {
            assertEquals(Optional.empty(),
                    first(ValuePatterns.seriesAndNumber(), "Seria si numarul de mai jos"));
        }
    }
}
