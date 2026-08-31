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

    @Nested
    @DisplayName("Fields added in 1.2.0")
    class NewShapes {

        @DisplayName("reads a trade-register number in any of its county forms")
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"J40/1122/2015", "J 12/345/2018", "F03/99/2001", "C40/1/1999"})
        void readsRegistrationNumbers(String printed) {
            assertTrue(first(ValuePatterns.registrationNumber(), "Reg. Com. " + printed).isPresent());
        }

        @Test
        @DisplayName("a date is not a register number, however similar the slashes look")
        void refusesADateAsARegistrationNumber() {
            assertEquals(Optional.empty(),
                    first(ValuePatterns.registrationNumber(), "emisa 05/03/2024"));
        }

        @Test
        @DisplayName("reads an account whether or not it is printed in groups")
        void readsIbans() {
            assertEquals(Optional.of("RO49 AAAA 1B31 0075 9384 0000"),
                    first(ValuePatterns.iban(), "IBAN: RO49 AAAA 1B31 0075 9384 0000"));
            assertEquals(Optional.of("RO49AAAA1B3100759384"),
                    first(ValuePatterns.iban(), "Cont RO49AAAA1B3100759384"));
        }

        @DisplayName("reads the currency an invoice is denominated in")
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"RON", "LEI", "EUR", "USD"})
        void readsCurrencies(String code) {
            assertEquals(Optional.of(code),
                    first(ValuePatterns.currency(), "Total de plata 100,00 " + code));
        }

        @Test
        @DisplayName("a currency code inside a word is not a currency")
        void refusesACurrencyInsideAWord() {
            assertEquals(Optional.empty(), first(ValuePatterns.currency(), "LEIPZIG"));
        }

        @Test
        @DisplayName("reads the VAT rate beside its own label and nowhere else")
        void readsTheVatRate() {
            assertEquals(Optional.of("19"), first(ValuePatterns.vatRate(), "TVA 19%"));
            assertEquals(Optional.of("21"), first(ValuePatterns.vatRate(), "T.V.A. 21 %"));
            assertEquals(Optional.empty(), first(ValuePatterns.vatRate(), "Discount 10%"));
        }

        @Test
        @DisplayName("reads a quantity as a small number, whole or fractional")
        void readsQuantities() {
            assertEquals(Optional.of("10"), first(ValuePatterns.quantity(), "10"));
            assertEquals(Optional.of("2,5"), first(ValuePatterns.quantity(), "2,5"));
        }
    }

    @Nested
    @DisplayName("Landmarks on the page")
    class Landmarks {

        @Test
        @DisplayName("the summary block starts at the net line, not at a Total column heading")
        void findsTheTotalsBlock() {
            assertTrue(ValuePatterns.TOTALS_MARKER.matcher("Total fara TVA   100,00").find());
            assertTrue(ValuePatterns.TOTALS_MARKER.matcher("   Total de plata 119,00").find());
            assertFalse(ValuePatterns.TOTALS_MARKER.matcher("Denumire    Cant    Total").find(),
                    "a Total column heading is not the totals block");
        }

        @Test
        @DisplayName("a heading row needs two headings, so a summary line cannot pass as one")
        void findsTheTableHeading() {
            assertTrue(ValuePatterns.TABLE_HEADING.matcher(
                    "Denumire                Cant    Pret      Valoare").find());
            assertTrue(ValuePatterns.TABLE_HEADING.matcher(
                    "Nr. crt  Descriere   Valoare").find());
            assertFalse(ValuePatterns.TABLE_HEADING.matcher("Valoare TVA   159,60").find());
        }
    }
}
