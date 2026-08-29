package com.invoiceocr.extraction.rules;

import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.extraction.CompanyNameExtractor;
import com.invoiceocr.extraction.ContextGatedExtractor;
import com.invoiceocr.extraction.ExtractionRule;
import com.invoiceocr.extraction.ExtractionRuleProvider;
import com.invoiceocr.extraction.LabelledValueExtractor;
import com.invoiceocr.extraction.RegionScopedExtractor;
import com.invoiceocr.extraction.ValueShapeExtractor;
import com.invoiceocr.extraction.normalization.Normalizers;
import com.invoiceocr.extraction.text.ValuePattern;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.List;

/**
 * Rule set for Romanian invoices.
 *
 * <p>Every field is a ladder of strategies rather than a single pattern,
 * ordered from the most specific to the most speculative. The first rung that
 * produces a value wins, so a well-labelled invoice is read exactly, and an
 * awkward one still gives an answer instead of {@code N/A}:</p>
 *
 * <ol>
 *   <li>the label with its value beside it,</li>
 *   <li>the label with its value on one of the next lines — column headings and
 *       block layouts,</li>
 *   <li>the value recognised by its own shape inside the right part of the
 *       page,</li>
 *   <li>the value recognised anywhere, or inferred (the largest amount is the
 *       total).</li>
 * </ol>
 *
 * <p>Patterns are matched against diacritic-folded text, so they are written in
 * plain ASCII and still match {@code Vânzător} and {@code Total de plată}, and
 * every digit position tolerates the letters OCR substitutes for digits.</p>
 */
public final class RomanianInvoiceRuleProvider implements ExtractionRuleProvider {

    private static final String SEP = ValuePatterns.SEPARATOR;

    private static final String SUPPLIER_LABEL =
            "\\b(?:Furnizor(?:ul)?|Vanzator(?:ul)?|Emitent(?:ul)?|Prestator(?:ul)?)\\b";

    /** Horizontal space only, so no label can run past the end of its line. */
    private static final String GAP = "[ \\t]*";

    private static final String FISCAL_LABEL =
            "\\b(?:C\\.?[ \\t]?U\\.?[ \\t]?I\\.?|C\\.?[ \\t]?I\\.?[ \\t]?F\\.?"
                    + "|Cod" + GAP + "(?:unic" + GAP + ")?(?:de" + GAP + ")?"
                    + "(?:inregistrare|identificare)?" + GAP + "fiscal[a]?)";

    private static final String INVOICE_WORD = "\\bFactur[a]" + GAP + "(?:fiscal[a]" + GAP + ")?";
    private static final String NUMBER_WORD = "(?:nr\\.?|no\\.?|num[a]r(?:ul)?)";

    /**
     * "TVA" that is not the tail of "fara TVA". Romanian invoices label the net
     * line "Total fara TVA" — without this guard that line's amount is reported
     * as the VAT, which was the single most misleading error in the old rules.
     */
    private static final String VAT_WORD = "(?<!fara )(?<!fara-)T\\.?[ \\t]?V\\.?[ \\t]?A\\.?";

    /** "Total" that does not introduce the net line. */
    private static final String TOTAL_WORD = "\\bTotal(?![ \\t]*fara)";

    /** The rest of a line, starting at a letter: a supplier name beside its label. */
    private static final ValuePattern REST_OF_LINE =
            ValuePattern.of("([\\p{L}][^\\r\\n]{2,})");

    /** A whole line that carries a legal form, wherever it starts. */
    private static final ValuePattern COMPANY_LINE = ValuePattern.of(
            "([^\\r\\n]*\\b(?:S\\.?R\\.?L\\.?|S\\.?A\\.?|PFA|SNC|SCS)\\b[^\\r\\n]*)",
            value -> value.trim().length() >= 4);

    @Override
    public List<ExtractionRule> rules() {
        return List.of(supplier(), invoiceNumber(), issueDate(), fiscalCode(), vatAmount(), totalAmount());
    }

    // ------------------------------------------------------------- supplier

    private static ExtractionRule supplier() {
        return ExtractionRule.forField(InvoiceFields.SUPPLIER)
                // "Furnizor: SC ALFA SRL"
                .using(LabelledValueExtractor.sameLine(SUPPLIER_LABEL + SEP, REST_OF_LINE))
                // "FURNIZOR" as a heading, the name on one of the next lines
                .using(LabelledValueExtractor.within(SUPPLIER_LABEL, COMPANY_LINE, 3))
                // No label: the company named in the supplier's half of the page
                .using(RegionScopedExtractor.inSupplierBlock(new CompanyNameExtractor()))
                // Letterhead only: the first company named anywhere
                .using(new CompanyNameExtractor())
                .normalizedBy(Normalizers.text())
                .build();
    }

    // ------------------------------------------------------- invoice number

    private static ExtractionRule invoiceNumber() {
        ValuePattern number = ValuePatterns.documentNumber();
        return ExtractionRule.forField(InvoiceFields.INVOICE_NUMBER)
                // "Seria ALF nr. 00420", where series and number are printed apart
                .using(ValueShapeExtractor.first(ValuePatterns.seriesAndNumber()))
                // "Factura fiscala nr. FCT-2024/0182", and the number one line below
                .using(LabelledValueExtractor.within(INVOICE_WORD + NUMBER_WORD + "?" + SEP, number, 1))
                // "Nr. factura" as a column heading, the value in the row beneath
                .using(LabelledValueExtractor.within(
                        "\\b" + NUMBER_WORD + GAP + "(?:factur[a])?" + SEP, number, 2))
                .using(LabelledValueExtractor.within("\\bSeria" + SEP, number, 1))
                .using(LabelledValueExtractor.within("\\bInvoice" + GAP + "(?:no\\.?|number)?" + SEP, number, 1))
                .normalizedBy(Normalizers.documentNumber())
                .build();
    }

    // ----------------------------------------------------------- issue date

    private static ExtractionRule issueDate() {
        ValuePattern date = ValuePatterns.date();
        return ExtractionRule.forField(InvoiceFields.ISSUE_DATE)
                // "Data facturii: 05.03.2024", and column headings above their values
                .using(LabelledValueExtractor.within(
                        "\\bData" + GAP + "(?:facturii|emiterii|facturarii)" + SEP, date, 2))
                // "Factura nr. 100234 din 08.08.2024"
                .using(LabelledValueExtractor.sameLine("\\bdin\\b" + SEP, date))
                .using(LabelledValueExtractor.within("\\bDat[ae]\\b" + SEP, date, 1))
                // Any plausible date on the page, as a last resort
                .using(ValueShapeExtractor.first(date))
                .normalizedBy(Normalizers.date())
                .build();
    }

    // ---------------------------------------------------------- fiscal code

    private static ExtractionRule fiscalCode() {
        ValuePattern bare = ValuePatterns.bareFiscalCode();
        ValuePattern prefixed = ValuePatterns.prefixedFiscalCode();
        return ExtractionRule.forField(InvoiceFields.FISCAL_CODE)
                // The supplier's own code, even when the buyer's is printed first
                .using(RegionScopedExtractor.inSupplierBlock(
                        LabelledValueExtractor.within(FISCAL_LABEL + SEP, bare, 1)))
                .using(RegionScopedExtractor.inSupplierBlock(ValueShapeExtractor.first(prefixed)))
                .using(LabelledValueExtractor.within(FISCAL_LABEL + SEP, bare, 1))
                .using(ValueShapeExtractor.first(prefixed))
                .normalizedBy(Normalizers.fiscalCode())
                .build();
    }

    // ------------------------------------------------------------------ VAT

    private static ExtractionRule vatAmount() {
        ValuePattern amount = ValuePatterns.amount();
        return ExtractionRule.forField(InvoiceFields.VAT_AMOUNT)
                .using(LabelledValueExtractor.sameLine(
                        "(?:Total|Valoare)" + GAP + VAT_WORD, amount))
                .using(LabelledValueExtractor.sameLine(VAT_WORD, amount))
                // "TVA" on its own line with the amount underneath
                .using(LabelledValueExtractor.within(VAT_WORD, amount, 2))
                .normalizedBy(Normalizers.amount())
                .build();
    }

    // ---------------------------------------------------------------- total

    private static ExtractionRule totalAmount() {
        ValuePattern amount = ValuePatterns.amount();
        return ExtractionRule.forField(InvoiceFields.TOTAL_AMOUNT)
                .using(LabelledValueExtractor.within(
                        "\\bTotal" + GAP + "(?:general|de" + GAP + "plat[a]|factur[a]|de" + GAP + "achitat)"
                                + SEP, amount, 2))
                .using(LabelledValueExtractor.sameLine(TOTAL_WORD + SEP, amount))
                // Nothing labelled: on an invoice the largest amount is the total.
                // Gated, so a page that never mentions a total is left alone rather
                // than handed a confident wrong number.
                .using(new ContextGatedExtractor(ValueShapeExtractor.largest(amount),
                        "\\b(?:total|suma|plat[a]|valoare|tva|factur[a])\\b"))
                .normalizedBy(Normalizers.amount())
                .build();
    }
}
