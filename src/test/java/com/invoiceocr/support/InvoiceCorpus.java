package com.invoiceocr.support;

import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Realistic OCR output from Romanian invoices, with the values a human reads
 * off each one.
 *
 * <p>These are transcriptions of the layouts that actually occur: labels above
 * values in a table, a supplier printed as a block, the buyer's block first,
 * totals right-aligned across a gap, and the character confusions Tesseract
 * makes on a mediocre scan. The parser is measured against this corpus, so a
 * claim that recognition improved is a number rather than an opinion.</p>
 */
public final class InvoiceCorpus {

    /** One invoice: a name, the OCR text, and what should be extracted from it. */
    public record Sample(String name, String text, Map<FieldDefinition, String> expected) { }

    public static List<Sample> all() {
        return List.of(
                labelledInline(),
                supplierBlock(),
                tableHeaderAboveValues(),
                rightAlignedTotals(),
                buyerBlockFirst(),
                ocrNoise(),
                uppercaseNoColons(),
                totalOnNextLine(),
                withDiacritics(),
                letterheadNoLabels());
    }

    private static Map<FieldDefinition, String> expect(String supplier, String number, String date,
                                                       String code, String vat, String total) {
        Map<FieldDefinition, String> expected = new LinkedHashMap<>();
        if (supplier != null) {
            expected.put(InvoiceFields.SUPPLIER, supplier);
        }
        if (number != null) {
            expected.put(InvoiceFields.INVOICE_NUMBER, number);
        }
        if (date != null) {
            expected.put(InvoiceFields.ISSUE_DATE, date);
        }
        if (code != null) {
            expected.put(InvoiceFields.FISCAL_CODE, code);
        }
        if (vat != null) {
            expected.put(InvoiceFields.VAT_AMOUNT, vat);
        }
        if (total != null) {
            expected.put(InvoiceFields.TOTAL_AMOUNT, total);
        }
        return expected;
    }

    /** The textbook layout: every label followed by its value on the same line. */
    private static Sample labelledInline() {
        return new Sample("labelled-inline", """
                FACTURA FISCALA
                Furnizor: SC EXEMPLU DISTRIBUTIE SRL
                CUI: RO 12345678
                Factura fiscala nr. FCT-2024/0182
                Data facturii: 05.03.2024

                Servicii consultanta IT 1 700,00
                Licenta software anuala 1 300,00
                Total 1.000,00
                Total TVA 19% 190,00
                Total de plata 1.190,00 LEI
                """,
                expect("SC EXEMPLU DISTRIBUTIE SRL", "FCT-2024/0182", "05.03.2024",
                        "RO12345678", "190.00", "1190.00"));
    }

    /** The supplier is a block under its heading, not a value beside it. */
    private static Sample supplierBlock() {
        return new Sample("supplier-block", """
                FURNIZOR
                SC ALFA CONSTRUCT SRL
                Str. Independentei nr. 12, Cluj-Napoca
                CUI: RO9876543
                Reg. Com.: J12/345/2018

                CUMPARATOR
                SC BETA COMERT SRL
                CUI: RO1112223

                FACTURA
                Seria ALF nr. 00420
                Data: 17.04.2024

                Total fara TVA          2.500,00
                TVA 19%                   475,00
                TOTAL DE PLATA          2.975,00
                """,
                expect("SC ALFA CONSTRUCT SRL", "ALF 00420", "17.04.2024",
                        "RO9876543", "475.00", "2975.00"));
    }

    /** Column headers on one line, their values on the next: very common. */
    private static Sample tableHeaderAboveValues() {
        return new Sample("table-header-above-values", """
                SC GAMMA LOGISTIC SRL
                CUI RO 445566
                J40/1122/2015

                Nr. factura     Data emiterii     Termen de plata
                GML-7781        02.02.2024        02.03.2024

                Denumire                Cant    Pret      Valoare
                Transport marfa            1   840,00      840,00

                Valoare TVA                              159,60
                Total de plata                           999,60
                """,
                expect("SC GAMMA LOGISTIC SRL", "GML-7781", "02.02.2024",
                        "RO445566", "159.60", "999.60"));
    }

    /** Totals right-aligned far from their labels, with dot thousands separators. */
    private static Sample rightAlignedTotals() {
        return new Sample("right-aligned-totals", """
                Furnizor SC DELTA PROD SA
                Cod fiscal RO 778899
                Factura nr. 100234 din 08.08.2024

                ------------------------------------------------------------
                Produs                                            Valoare
                Ambalaje carton                                 12.400,00
                Manopera                                         1.100,00
                ------------------------------------------------------------
                                              Total fara TVA    13.500,00
                                              TVA 19%            2.565,00
                                              TOTAL             16.065,00
                """,
                expect("SC DELTA PROD SA", "100234", "08.08.2024",
                        "RO778899", "2565.00", "16065.00"));
    }

    /** The buyer is printed first, so the first company and code are the wrong ones. */
    private static Sample buyerBlockFirst() {
        return new Sample("buyer-block-first", """
                CUMPARATOR: SC OMEGA RETAIL SRL
                CUI: RO 5550001
                Adresa: Bd. Unirii 3, Bucuresti

                FURNIZOR: SC EPSILON SERVICE SRL
                CUI: RO 6660002

                Factura nr. EPS-2024-88 / 21.06.2024

                Total                                   4.000,00
                TVA 19%                                   760,00
                Total de plata                          4.760,00
                """,
                expect("SC EPSILON SERVICE SRL", "EPS-2024-88", "21.06.2024",
                        "RO6660002", "760.00", "4760.00"));
    }

    /** A mediocre scan: O for 0, l for 1, S for 5, and no diacritics. */
    private static Sample ocrNoise() {
        return new Sample("ocr-noise", """
                Furnizor: SC ZETA TRADING SRL
                C.U.I.: RO 33O44SS
                Factura nr. ZT-OO91
                Data facturii: O9.O9.2O24

                Total fara TVA                          l.200,00
                TVA 19%                                   228,OO
                Total de plata                          l.428,OO
                """,
                expect("SC ZETA TRADING SRL", "ZT-0091", "09.09.2024",
                        "RO3304455", "228.00", "1428.00"));
    }

    /** All caps, no colons and no punctuation after the labels. */
    private static Sample uppercaseNoColons() {
        return new Sample("uppercase-no-colons", """
                FURNIZOR SC THETA INDUSTRIES SRL
                COD FISCAL RO 221100
                FACTURA NR TH 5567
                DATA 30.11.2024

                TOTAL FARA TVA 900,00
                TVA 171,00
                TOTAL DE PLATA 1071,00
                """,
                expect("SC THETA INDUSTRIES SRL", "TH 5567", "30.11.2024",
                        "RO221100", "171.00", "1071.00"));
    }

    /** Each total label on one line, its amount on the following line. */
    private static Sample totalOnNextLine() {
        return new Sample("total-on-next-line", """
                SC KAPPA DESIGN SRL
                Furnizor
                CUI RO 909090
                Factura numarul KD-333
                Data emiterii
                14.14.2024
                Data facturii 14.12.2024

                Total de plata
                3.599,99 LEI
                TVA
                574,60
                """,
                expect("SC KAPPA DESIGN SRL", "KD-333", "14.12.2024",
                        "RO909090", "574.60", "3599.99"));
    }

    /** Diacritics present, as a good scan of a properly typeset invoice gives. */
    private static Sample withDiacritics() {
        return new Sample("with-diacritics", """
                Vânzător: SC LAMBDA SOLUȚII SRL
                Cod de identificare fiscală: RO 314159
                Factură fiscală nr. LMB-0007
                Data facturii: 03.03.2024

                Total fără TVA                            5.000,00
                TVA 19%                                     950,00
                Total de plată                            5.950,00
                """,
                // Matching folds the diacritics; the value keeps them.
                expect("SC LAMBDA SOLUȚII SRL", "LMB-0007", "03.03.2024",
                        "RO314159", "950.00", "5950.00"));
    }

    /** Letterhead only: the company name has no label anywhere near it. */
    private static Sample letterheadNoLabels() {
        return new Sample("letterhead-no-labels", """
                SC SIGMA MEDICAL SRL
                Str. Sanatatii 8, Timisoara
                RO 2468101
                Tel 0256 123 456

                FACTURA
                Nr. SM-2024-451 din 12.05.2024

                Consultatii                                 300,00
                Analize                                     450,00

                TVA 19%                                     142,50
                Total de plata                              892,50
                """,
                expect("SC SIGMA MEDICAL SRL", "SM-2024-451", "12.05.2024",
                        "RO2468101", "142.50", "892.50"));
    }

    private InvoiceCorpus() {
        throw new AssertionError("No instances");
    }
}
