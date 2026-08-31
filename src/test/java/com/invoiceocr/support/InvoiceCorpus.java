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
 *
 * <p>Every fiscal code here carries a control digit that adds up. That is not
 * decoration: the rules verify the checksum and prefer a candidate that passes
 * it, so a corpus of invented codes would exercise the fallback path on every
 * sample and never the one that matters.</p>
 */
public final class InvoiceCorpus {

    /**
     * One invoice: a name, the OCR text, the fields that should be extracted
     * from it, and the rows of its goods table when it has one.
     */
    public record Sample(String name, String text,
                         Map<FieldDefinition, String> expected,
                         List<String> expectedItems) {

        public Sample(String name, String text, Map<FieldDefinition, String> expected) {
            this(name, text, expected, List.of());
        }
    }

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
                letterheadNoLabels(),
                vatDerivedFromNetAndTotal(),
                paymentTermInDays(),
                bankAccountAndRegistry(),
                itemisedTable(),
                impossibleDateSkipped(),
                foreignCurrency(),
                misreadTotalCorrected(),
                grossOnlyWithPrintedRate());
    }

    // ------------------------------------------------------- expectations

    /**
     * Fluent expectations, because twelve positional arguments would be
     * unreadable and eleven of them would be null on most samples.
     */
    public static final class Expect {

        private final Map<FieldDefinition, String> values = new LinkedHashMap<>();

        private Expect put(FieldDefinition field, String value) {
            values.put(field, value);
            return this;
        }

        public Expect supplier(String value) {
            return put(InvoiceFields.SUPPLIER, value);
        }

        public Expect buyer(String value) {
            return put(InvoiceFields.BUYER, value);
        }

        public Expect number(String value) {
            return put(InvoiceFields.INVOICE_NUMBER, value);
        }

        public Expect issued(String value) {
            return put(InvoiceFields.ISSUE_DATE, value);
        }

        public Expect due(String value) {
            return put(InvoiceFields.DUE_DATE, value);
        }

        public Expect fiscalCode(String value) {
            return put(InvoiceFields.FISCAL_CODE, value);
        }

        public Expect registry(String value) {
            return put(InvoiceFields.REGISTRATION_NUMBER, value);
        }

        public Expect iban(String value) {
            return put(InvoiceFields.IBAN, value);
        }

        public Expect net(String value) {
            return put(InvoiceFields.NET_AMOUNT, value);
        }

        public Expect vat(String value) {
            return put(InvoiceFields.VAT_AMOUNT, value);
        }

        public Expect total(String value) {
            return put(InvoiceFields.TOTAL_AMOUNT, value);
        }

        public Expect currency(String value) {
            return put(InvoiceFields.CURRENCY, value);
        }

        public Map<FieldDefinition, String> build() {
            return Map.copyOf(values);
        }
    }

    private static Expect expect() {
        return new Expect();
    }

    // ------------------------------------------------------------ samples

    /** The textbook layout: every label followed by its value on the same line. */
    private static Sample labelledInline() {
        return new Sample("labelled-inline", """
                FACTURA FISCALA
                Furnizor: SC EXEMPLU DISTRIBUTIE SRL
                CUI: RO 12345674
                Factura fiscala nr. FCT-2024/0182
                Data facturii: 05.03.2024

                Servicii consultanta IT 1 700,00
                Licenta software anuala 1 300,00
                Total 1.000,00
                Total TVA 19% 190,00
                Total de plata 1.190,00 LEI
                """,
                expect().supplier("SC EXEMPLU DISTRIBUTIE SRL")
                        .number("FCT-2024/0182")
                        .issued("05.03.2024")
                        .fiscalCode("RO12345674")
                        .net("1000.00")
                        .vat("190.00")
                        .total("1190.00")
                        .currency("RON")
                        .build());
    }

    /** The supplier is a block under its heading, not a value beside it. */
    private static Sample supplierBlock() {
        return new Sample("supplier-block", """
                FURNIZOR
                SC ALFA CONSTRUCT SRL
                Str. Independentei nr. 12, Cluj-Napoca
                CUI: RO9876544
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
                expect().supplier("SC ALFA CONSTRUCT SRL")
                        .buyer("SC BETA COMERT SRL")
                        .number("ALF 00420")
                        .issued("17.04.2024")
                        .fiscalCode("RO9876544")
                        .registry("J12/345/2018")
                        .net("2500.00")
                        .vat("475.00")
                        .total("2975.00")
                        .build());
    }

    /** Column headers on one line, their values on the next: very common. */
    private static Sample tableHeaderAboveValues() {
        return new Sample("table-header-above-values", """
                SC GAMMA LOGISTIC SRL
                CUI RO 445564
                J40/1122/2015

                Nr. factura     Data emiterii     Termen de plata
                GML-7781        02.02.2024        02.03.2024

                Denumire                Cant    Pret      Valoare
                Transport marfa            1   840,00      840,00

                Valoare TVA                              159,60
                Total de plata                           999,60
                """,
                expect().supplier("SC GAMMA LOGISTIC SRL")
                        .number("GML-7781")
                        .issued("02.02.2024")
                        .due("02.03.2024")
                        .fiscalCode("RO445564")
                        .registry("J40/1122/2015")
                        .net("840.00")
                        .vat("159.60")
                        .total("999.60")
                        .build(),
                List.of("Transport marfa"));
    }

    /** Totals right-aligned far from their labels, with dot thousands separators. */
    private static Sample rightAlignedTotals() {
        return new Sample("right-aligned-totals", """
                Furnizor SC DELTA PROD SA
                Cod fiscal RO 778895
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
                expect().supplier("SC DELTA PROD SA")
                        .number("100234")
                        .issued("08.08.2024")
                        .fiscalCode("RO778895")
                        .net("13500.00")
                        .vat("2565.00")
                        .total("16065.00")
                        .build(),
                List.of("Ambalaje carton", "Manopera"));
    }

    /** The buyer is printed first, so the first company and code are the wrong ones. */
    private static Sample buyerBlockFirst() {
        return new Sample("buyer-block-first", """
                CUMPARATOR: SC OMEGA RETAIL SRL
                CUI: RO 5550005
                Adresa: Bd. Unirii 3, Bucuresti

                FURNIZOR: SC EPSILON SERVICE SRL
                CUI: RO 6660006

                Factura nr. EPS-2024-88 / 21.06.2024

                Total                                   4.000,00
                TVA 19%                                   760,00
                Total de plata                          4.760,00
                """,
                expect().supplier("SC EPSILON SERVICE SRL")
                        .buyer("SC OMEGA RETAIL SRL")
                        .number("EPS-2024-88")
                        .issued("21.06.2024")
                        .fiscalCode("RO6660006")
                        .net("4000.00")
                        .vat("760.00")
                        .total("4760.00")
                        .build());
    }

    /** A mediocre scan: O for 0, l for 1, S for 5, and no diacritics. */
    private static Sample ocrNoise() {
        return new Sample("ocr-noise", """
                Furnizor: SC ZETA TRADING SRL
                C.U.I.: RO 33O44S4
                Factura nr. ZT-OO91
                Data facturii: O9.O9.2O24

                Total fara TVA                          l.200,00
                TVA 19%                                   228,OO
                Total de plata                          l.428,OO
                """,
                expect().supplier("SC ZETA TRADING SRL")
                        .number("ZT-0091")
                        .issued("09.09.2024")
                        .fiscalCode("RO3304454")
                        .net("1200.00")
                        .vat("228.00")
                        .total("1428.00")
                        .build());
    }

    /** All caps, no colons and no punctuation after the labels. */
    private static Sample uppercaseNoColons() {
        return new Sample("uppercase-no-colons", """
                FURNIZOR SC THETA INDUSTRIES SRL
                COD FISCAL RO 221109
                FACTURA NR TH 5567
                DATA 30.11.2024

                TOTAL FARA TVA 900,00
                TVA 171,00
                TOTAL DE PLATA 1071,00
                """,
                expect().supplier("SC THETA INDUSTRIES SRL")
                        .number("TH 5567")
                        .issued("30.11.2024")
                        .fiscalCode("RO221109")
                        .net("900.00")
                        .vat("171.00")
                        .total("1071.00")
                        .build());
    }

    /** Each total label on one line, its amount on the following line. */
    private static Sample totalOnNextLine() {
        return new Sample("total-on-next-line", """
                SC KAPPA DESIGN SRL
                Furnizor
                CUI RO 909095
                Factura numarul KD-333
                Data emiterii
                14.14.2024
                Data facturii 14.12.2024

                Total de plata
                3.599,99 LEI
                TVA
                574,60
                """,
                expect().supplier("SC KAPPA DESIGN SRL")
                        .number("KD-333")
                        .issued("14.12.2024")
                        .fiscalCode("RO909095")
                        .vat("574.60")
                        .total("3599.99")
                        .net("3025.39")
                        .currency("RON")
                        .build());
    }

    /** Diacritics present, as a good scan of a properly typeset invoice gives. */
    private static Sample withDiacritics() {
        return new Sample("with-diacritics", """
                Vânzător: SC LAMBDA SOLUȚII SRL
                Cod de identificare fiscală: RO 314151
                Factură fiscală nr. LMB-0007
                Data facturii: 03.03.2024

                Total fără TVA                            5.000,00
                TVA 19%                                     950,00
                Total de plată                            5.950,00
                """,
                // Matching folds the diacritics; the value keeps them.
                expect().supplier("SC LAMBDA SOLUȚII SRL")
                        .number("LMB-0007")
                        .issued("03.03.2024")
                        .fiscalCode("RO314151")
                        .net("5000.00")
                        .vat("950.00")
                        .total("5950.00")
                        .build());
    }

    /** Letterhead only: the company name has no label anywhere near it. */
    private static Sample letterheadNoLabels() {
        return new Sample("letterhead-no-labels", """
                SC SIGMA MEDICAL SRL
                Str. Sanatatii 8, Timisoara
                RO 2468106
                Tel 0256 123 456

                FACTURA
                Nr. SM-2024-451 din 12.05.2024

                Consultatii                                 300,00
                Analize                                     450,00

                TVA 19%                                     142,50
                Total de plata                              892,50
                """,
                expect().supplier("SC SIGMA MEDICAL SRL")
                        .number("SM-2024-451")
                        .issued("12.05.2024")
                        .fiscalCode("RO2468106")
                        .net("750.00")
                        .vat("142.50")
                        .total("892.50")
                        .build());
    }

    // ---------------------------------------------------- added in 1.2.0

    /**
     * The net line and the amount due are printed; the VAT is not. It follows
     * exactly from the other two, and is the commonest thing 1.1 reported as
     * {@code N/A} on an otherwise perfectly readable invoice.
     */
    private static Sample vatDerivedFromNetAndTotal() {
        return new Sample("vat-derived", """
                Furnizor: SC MIU SERVICII SRL
                CUI: RO 1234565
                Factura nr. MIU-4410
                Data facturii: 11.09.2024

                Total fara TVA                            2.000,00
                Total de plata                            2.380,00 RON
                """,
                expect().supplier("SC MIU SERVICII SRL")
                        .number("MIU-4410")
                        .issued("11.09.2024")
                        .fiscalCode("RO1234565")
                        .net("2000.00")
                        .vat("380.00")
                        .total("2380.00")
                        .currency("RON")
                        .build());
    }

    /**
     * No due date is printed, but the payment term is. Adding it to the issue
     * date gives the date the invoice actually falls due.
     */
    private static Sample paymentTermInDays() {
        return new Sample("payment-term", """
                Furnizor: SC NU LOGISTICS SRL
                CUI: RO 5544330
                Factura nr. NU-2024-17
                Data facturii: 01.10.2024
                Termen de plata: 30 zile

                Total fara TVA                            1.000,00
                TVA 19%                                     190,00
                Total de plata                            1.190,00
                """,
                expect().supplier("SC NU LOGISTICS SRL")
                        .number("NU-2024-17")
                        .issued("01.10.2024")
                        .due("31.10.2024")
                        .fiscalCode("RO5544330")
                        .net("1000.00")
                        .vat("190.00")
                        .total("1190.00")
                        .build());
    }

    /**
     * A supplier block carrying the two identifiers an accountant needs in order
     * to pay the thing: the trade-register number and the bank account. The IBAN
     * is a real one in shape and passes mod-97.
     */
    private static Sample bankAccountAndRegistry() {
        return new Sample("bank-and-registry", """
                FURNIZOR
                SC XI CONSULTING SRL
                CUI: RO 445564
                Reg. Com. J40/9988/2011
                Banca Transilvania
                IBAN: RO49 AAAA 1B31 0075 9384 0000

                CUMPARATOR
                SC OMICRON SA
                CUI: RO 1112223

                Factura nr. XI-0099 din 04.04.2024

                Total fara TVA                              800,00
                TVA 19%                                     152,00
                Total de plata                              952,00
                """,
                expect().supplier("SC XI CONSULTING SRL")
                        .buyer("SC OMICRON SA")
                        .number("XI-0099")
                        .issued("04.04.2024")
                        .fiscalCode("RO445564")
                        .registry("J40/9988/2011")
                        .iban("RO49 AAAA 1B31 0075 9384 0000")
                        .net("800.00")
                        .vat("152.00")
                        .total("952.00")
                        .build());
    }

    /** A proper goods table: four rows, three columns of figures, a total below. */
    private static Sample itemisedTable() {
        return new Sample("itemised-table", """
                Furnizor: SC PI MATERIALE SRL
                CUI: RO 778895
                Factura nr. PI-7788 din 19.07.2024

                Nr. crt  Denumire produs        Cant     Pret      Valoare
                1        Ciment Portland          10    32,00       320,00
                2        Nisip spalat sac         25     8,00       200,00
                3        Caramida BCA            100     4,50       450,00
                4        Transport                 1    30,00        30,00

                Total fara TVA                                    1.000,00
                TVA 19%                                             190,00
                Total de plata                                    1.190,00
                """,
                expect().supplier("SC PI MATERIALE SRL")
                        .number("PI-7788")
                        .issued("19.07.2024")
                        .fiscalCode("RO778895")
                        .net("1000.00")
                        .vat("190.00")
                        .total("1190.00")
                        .build(),
                List.of("Ciment Portland", "Nisip spalat sac", "Caramida BCA", "Transport"));
    }

    /**
     * The first date on the page does not exist. The calendar check rejects it
     * and the rule falls through to the one that does — where 1.1 would have
     * printed 31 February on the report.
     */
    private static Sample impossibleDateSkipped() {
        return new Sample("impossible-date", """
                Furnizor: SC RHO SERVICE SRL
                CUI: RO 909095
                Contract incheiat 31.02.2024
                Factura nr. RHO-12
                Data facturii: 05.05.2024

                Total fara TVA                              500,00
                TVA 19%                                      95,00
                Total de plata                              595,00
                """,
                expect().supplier("SC RHO SERVICE SRL")
                        .number("RHO-12")
                        .issued("05.05.2024")
                        .fiscalCode("RO909095")
                        .net("500.00")
                        .vat("95.00")
                        .total("595.00")
                        .build());
    }

    /** Invoiced in euro, which the currency field has to report rather than assume. */
    private static Sample foreignCurrency() {
        return new Sample("foreign-currency", """
                Furnizor: SC SIGMA EXPORT SRL
                CUI: RO 221109
                Factura nr. EXP-2024-3 din 22.02.2024

                Total fara TVA                            1.500,00 EUR
                TVA 19%                                     285,00 EUR
                Total de plata                            1.785,00 EUR
                """,
                expect().supplier("SC SIGMA EXPORT SRL")
                        .number("EXP-2024-3")
                        .issued("22.02.2024")
                        .fiscalCode("RO221109")
                        .net("1500.00")
                        .vat("285.00")
                        .total("1785.00")
                        .currency("EUR")
                        .build());
    }

    /**
     * The three amounts do not add up, because the total lost a digit to the
     * scan. The net and the VAT imply a rate that exists, so they are believed
     * and the total is recomputed from them.
     */
    private static Sample misreadTotalCorrected() {
        return new Sample("misread-total", """
                Furnizor: SC TAU DISTRIBUTIE SRL
                CUI: RO 12345674
                Factura nr. TAU-555 din 08.03.2024

                Total fara TVA                            3.000,00
                TVA 19%                                     570,00
                Total de plata                              370,00
                """,
                expect().supplier("SC TAU DISTRIBUTIE SRL")
                        .number("TAU-555")
                        .issued("08.03.2024")
                        .fiscalCode("RO12345674")
                        .net("3000.00")
                        .vat("570.00")
                        .total("3570.00")
                        .build());
    }

    /**
     * A till-style invoice that prints only the gross figure, with the rate
     * beside it. Both other amounts follow from those two.
     */
    private static Sample grossOnlyWithPrintedRate() {
        return new Sample("gross-only", """
                SC UPSILON RETAIL SRL
                CUI RO 2468106
                Bon fiscal / Factura simplificata
                Nr. UPS-8080 din 06.06.2024

                Suma include TVA 19%
                Total de plata                              119,00 LEI
                """,
                expect().supplier("SC UPSILON RETAIL SRL")
                        .number("UPS-8080")
                        .issued("06.06.2024")
                        .fiscalCode("RO2468106")
                        .net("100.00")
                        .vat("19.00")
                        .total("119.00")
                        .currency("RON")
                        .build());
    }

    private InvoiceCorpus() {
        throw new AssertionError("No instances");
    }
}
