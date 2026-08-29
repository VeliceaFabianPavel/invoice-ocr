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

@DisplayName("Romanian invoice rules")
class RomanianInvoiceRuleProviderTest {

    private final InvoiceParser parser = new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider());

    @Test
    @DisplayName("extracts every field from a typical layout")
    void extractsTypicalInvoice() {
        InvoiceData data = parser.parse(RecognizedText.of("""
                SC EXEMPLU DISTRIBUTIE SRL
                Furnizor: SC EXEMPLU DISTRIBUTIE SRL
                CUI: RO 12345678
                Factura fiscala nr. FCT-2024/0182
                Data facturii: 5.3.2024

                Denumire produs           Cantitate     Valoare
                Servicii consultanta              1     1.000,00

                Total                                   1.000,00
                Total TVA 19%                             190,00
                Total de plata                          1.190,00 LEI
                """));

        assertEquals(Optional.of("SC EXEMPLU DISTRIBUTIE SRL"), data.valueOf(InvoiceFields.SUPPLIER));
        assertEquals(Optional.of("FCT-2024/0182"), data.valueOf(InvoiceFields.INVOICE_NUMBER));
        assertEquals(Optional.of("05.03.2024"), data.valueOf(InvoiceFields.ISSUE_DATE));
        assertEquals(Optional.of("RO12345678"), data.valueOf(InvoiceFields.FISCAL_CODE));
        assertEquals(Optional.of("190.00"), data.valueOf(InvoiceFields.VAT_AMOUNT));
        assertEquals(Optional.of("1190.00"), data.valueOf(InvoiceFields.TOTAL_AMOUNT));
        assertEquals(6, data.recognizedCount());
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
    @DisplayName("reports unreadable text as six missing fields instead of failing")
    void survivesGarbageInput() {
        InvoiceData data = parser.parse(RecognizedText.of("|||  ~~~ 8B8B8 ???"));

        assertEquals(6, data.fields().size());
        assertEquals(0, data.recognizedCount());
        assertTrue(data.fields().stream().noneMatch(field -> field.isPresent()));
    }
}
