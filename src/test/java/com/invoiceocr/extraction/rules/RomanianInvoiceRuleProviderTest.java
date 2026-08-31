package com.invoiceocr.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules on their own, with none of the refinements behind them.
 *
 * <p>Everything asserted here is a value actually printed on the page, so a
 * failure means a pattern stopped matching — never that a derivation changed.
 * The refinements are measured separately.</p>
 */
@DisplayName("Romanian invoice rules")
class RomanianInvoiceRuleProviderTest {

    private final InvoiceParser parser = new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider());

    @Test
    @DisplayName("extracts every field from a typical layout")
    void extractsTypicalInvoice() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                SC EXEMPLU DISTRIBUTIE SRL
                Furnizor: SC EXEMPLU DISTRIBUTIE SRL
                CUI: RO 12345674
                Factura fiscala nr. FCT-2024/0182
                Data facturii: 5.3.2024

                Denumire produs           Cantitate     Valoare
                Servicii consultanta              1     1.000,00

                Total fara TVA                          1.000,00
                Total TVA 19%                             190,00
                Total de plata                          1.190,00 LEI
                """));

        assertEquals(Optional.of("SC EXEMPLU DISTRIBUTIE SRL"), data.valueOf(InvoiceFields.SUPPLIER));
        assertEquals(Optional.of("FCT-2024/0182"), data.valueOf(InvoiceFields.INVOICE_NUMBER));
        assertEquals(Optional.of("05.03.2024"), data.valueOf(InvoiceFields.ISSUE_DATE));
        assertEquals(Optional.of("RO12345674"), data.valueOf(InvoiceFields.FISCAL_CODE));
        assertEquals(Optional.of("1000.00"), data.valueOf(InvoiceFields.NET_AMOUNT));
        assertEquals(Optional.of("190.00"), data.valueOf(InvoiceFields.VAT_AMOUNT));
        assertEquals(Optional.of("1190.00"), data.valueOf(InvoiceFields.TOTAL_AMOUNT));
        assertEquals(Optional.of("RON"), data.valueOf(InvoiceFields.CURRENCY));
        assertEquals(8, data.recognizedCount());
    }

    @Test
    @DisplayName("prefers 'Total de plata' over the bare 'Total' printed above it")
    void prefersTheMostSpecificTotal() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                Total 1.000,00
                Total de plata 1.190,00
                """));

        assertEquals(Optional.of("1190.00"), data.valueOf(InvoiceFields.TOTAL_AMOUNT));
    }

    @Test
    @DisplayName("accepts CIF, diacritics and alternative supplier labels")
    void acceptsLayoutVariants() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                Vânzător: ALFA BETA SA
                C.I.F.: 987654
                Seria si numarul: AB 1024
                Data: 31-12-23
                """));

        assertEquals(Optional.of("ALFA BETA SA"), data.valueOf(InvoiceFields.SUPPLIER));
        assertEquals(Optional.of("987654"), data.valueOf(InvoiceFields.FISCAL_CODE));
        assertEquals(Optional.of("AB 1024"), data.valueOf(InvoiceFields.INVOICE_NUMBER));
        assertEquals(Optional.of("31.12.2023"), data.valueOf(InvoiceFields.ISSUE_DATE));
    }

    @Test
    @DisplayName("reads the second party, the register number and the bank account")
    void readsTheFieldsAddedIn12() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                FURNIZOR
                SC ALFA CONSTRUCT SRL
                CUI: RO 9876544
                Reg. Com. J12/345/2018
                IBAN: RO49 AAAA 1B31 0075 9384 0000

                CUMPARATOR
                SC BETA COMERT SRL
                CUI: RO 1112223

                Factura nr. ALF-1 din 01.02.2024
                Termen de plata: 15.02.2024
                Total fara TVA 100,00
                """));

        assertEquals(Optional.of("SC ALFA CONSTRUCT SRL"), data.valueOf(InvoiceFields.SUPPLIER));
        assertEquals(Optional.of("SC BETA COMERT SRL"), data.valueOf(InvoiceFields.BUYER));
        assertEquals(Optional.of("J12/345/2018"), data.valueOf(InvoiceFields.REGISTRATION_NUMBER));
        assertEquals(Optional.of("RO49 AAAA 1B31 0075 9384 0000"), data.valueOf(InvoiceFields.IBAN));
        assertEquals(Optional.of("15.02.2024"), data.valueOf(InvoiceFields.DUE_DATE));
        assertEquals(Optional.of("100.00"), data.valueOf(InvoiceFields.NET_AMOUNT));
    }

    @Test
    @DisplayName("the supplier's fiscal code wins over the buyer's, whichever is printed first")
    void picksTheSuppliersFiscalCode() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                CUMPARATOR: SC OMEGA RETAIL SRL
                CUI: RO 5550005

                FURNIZOR: SC EPSILON SERVICE SRL
                CUI: RO 6660006
                """));

        assertEquals(Optional.of("RO6660006"), data.valueOf(InvoiceFields.FISCAL_CODE));
    }

    @Test
    @DisplayName("an account that fails its checksum is not reported at all")
    void refusesAnUnverifiableAccount() {
        InvoiceData data = parser.parse(RecognizedText.of(
                "Furnizor: SC ALFA SRL\nIBAN: RO49 AAAA 1B31 0075 9384 0001\n"));

        assertTrue(data.valueOf(InvoiceFields.IBAN).isEmpty(),
                "a wrong bank account is worse than none");
    }

    @Test
    @DisplayName("no buyer on the page means no buyer reported, not the supplier twice")
    void willNotInventABuyer() {
        InvoiceData data = parser.parse(RecognizedText.of(
                "SC SIGMA MEDICAL SRL\nRO 2468106\nFACTURA\n"));

        assertEquals(Optional.of("SC SIGMA MEDICAL SRL"), data.valueOf(InvoiceFields.SUPPLIER));
        assertTrue(data.valueOf(InvoiceFields.BUYER).isEmpty());
    }

    @Test
    @DisplayName("reports unreadable text as missing fields instead of failing")
    void survivesGarbageInput() {
        InvoiceData data = parser.parse(RecognizedText.of("|||  ~~~ 8B8B8 ???"));

        assertEquals(InvoiceFields.ALL.size(), data.fields().size());
        assertEquals(0, data.recognizedCount());
        assertTrue(data.fields().stream().noneMatch(field -> field.isPresent()));
    }
}
