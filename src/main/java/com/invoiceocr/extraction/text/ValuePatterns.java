package com.invoiceocr.extraction.text;

import java.util.regex.Pattern;

/**
 * The shapes an invoice value takes, written to survive a mediocre scan.
 *
 * <p>Every digit position accepts the characters Tesseract confuses digits with
 * — {@code O} for 0, {@code l} and {@code I} for 1, {@code S} for 5, {@code B}
 * for 8, {@code Z} for 2 — because a pattern that insists on {@code [0-9]}
 * rejects {@code l.428,OO} outright, and a rejected value is a field the user
 * has to type by hand. The characters are put back by the normalisers, and each
 * pattern is paired with a check that at least one real digit was present, so
 * an ordinary word can never pass as a number.</p>
 */
public final class ValuePatterns {

    /** A digit, or a letter Tesseract commonly returns instead of one. */
    public static final String DIGIT = "[0-9OISBZl]";

    /**
     * Label separator: a colon, a dash, or nothing at all.
     *
     * <p>Spaces and tabs only, never {@code \s}: a newline inside a label would
     * let the label itself slide onto the following line, and the search window
     * would then start a line too far down. Reaching the next line is the
     * window's job, not the label's.</p>
     */
    public static final String SEPARATOR = "[ \\t]*[:.\\-]?[ \\t]*";

    private static final Pattern REAL_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern DATE_SHAPE = Pattern.compile(
            "^" + DIGIT + "{1,2}[./\\-]" + DIGIT + "{1,2}[./\\-]" + DIGIT + "{2,4}$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_ANYWHERE = Pattern.compile(
            DIGIT + "{1,2}[./\\-]" + DIGIT + "{1,2}[./\\-]" + DIGIT + "{2,4}",
            Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------- amounts

    /**
     * A monetary amount: optional thousands groups, optional two decimals.
     *
     * <p>It deliberately refuses to match a date ({@code 05.03.2024}) or a
     * percentage ({@code 19%}), the two things that used to be picked up as
     * amounts and reported as the VAT figure.</p>
     */
    public static ValuePattern amount() {
        // The thousands separator may be a dot, a comma or a space: which one is
        // the decimal point is decided later, by how many digits follow it.
        String number = "(?:" + DIGIT + "{1,3}(?:[., ]" + DIGIT + "{3})+|" + DIGIT + "+)"
                + "(?:[,.]" + DIGIT + "{2})?";
        return ValuePattern.of("(?<![\\w.,])(" + number + ")(?![\\w.,]*\\d)(?!\\s*%)",
                ValuePatterns::hasRealDigit);
    }

    // --------------------------------------------------------------- dates

    /** A day-first date, validated so an impossible one is skipped, not returned. */
    public static ValuePattern date() {
        return ValuePattern.of(
                "(?<![\\w])(" + DIGIT + "{1,2}[./\\-]" + DIGIT + "{1,2}[./\\-]" + DIGIT + "{2,4})(?![\\w])",
                ValuePatterns::isPlausibleDate);
    }

    /** True when the text parses as a real calendar date after digit repair. */
    public static boolean isPlausibleDate(String text) {
        String[] parts = OcrDigits.repair(text).split("[./\\-]");
        if (parts.length != 3) {
            return false;
        }
        try {
            int day = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            int year = Integer.parseInt(parts[2].trim());
            if (year < 100) {
                year += 2000;
            }
            return day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1990 && year <= 2999;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // -------------------------------------------------------- fiscal codes

    /** A fiscal code carrying its country prefix, safe to look for anywhere. */
    public static ValuePattern prefixedFiscalCode() {
        return ValuePattern.of("(?<![\\w])(R[O0][ .\\-]?" + DIGIT + "{2,10})(?![\\w])",
                ValuePatterns::hasRealDigit);
    }

    /** A fiscal code with the prefix optional: only safe right after a CUI label. */
    public static ValuePattern bareFiscalCode() {
        return ValuePattern.of("(?<![\\w])((?:R[O0][ .\\-]?)?" + DIGIT + "{2,10})(?![\\w])",
                text -> countRealDigits(text) >= 2);
    }

    // ------------------------------------------------------ document number

    /**
     * An invoice number: an optional letter series, then a run that must contain
     * a digit. Dates are excluded, or "Factura nr. 100234 din 08.08.2024" would
     * report the date as the number.
     */
    public static ValuePattern documentNumber() {
        return ValuePattern.of(
                "(?<![\\w])((?:[A-Z]{1,6}[ .\\-/])?" + DIGIT + "[0-9A-Z.\\-/]{0,19})(?![\\w])",
                text -> hasRealDigit(text) && text.length() >= 2 && !containsDate(text));
    }

    /**
     * "Seria AB nr. 1024", where the series and the number are printed apart.
     *
     * <p>The digit class accepts letters, so without the real-digit check
     * "Seria si numarul:" reads as series {@code s} and number {@code i}.</p>
     */
    public static ValuePattern seriesAndNumber() {
        return ValuePattern.joining(
                "Seria" + SEPARATOR + "([A-Z]{1,6})[ \\t]*(?:si[ \\t]*num[a]rul|nr\\.?|num[a]rul|num[a]r)?"
                        + SEPARATOR + "(" + DIGIT + "{1,12})",
                " ", 1, 2)
                .requiring(ValuePatterns::hasRealDigit);
    }

    // ---------------------------------------------------- registration number

    /**
     * A trade-register number: {@code J40/1122/2015}, and the county-letter
     * variants ({@code F}, {@code C}) that the older registries used.
     *
     * <p>The three parts are fixed in shape, which makes this one of the few
     * values on an invoice that can be found reliably with no label at all.</p>
     */
    public static ValuePattern registrationNumber() {
        return ValuePattern.of(
                "(?<![\\w])([JFC][ .]?" + DIGIT + "{1,2}[ ]?/[ ]?" + DIGIT + "{1,7}[ ]?/[ ]?" + DIGIT + "{4})(?![\\w])",
                ValuePatterns::hasRealDigit);
    }

    // -------------------------------------------------------------- IBAN

    /**
     * A bank account in IBAN form, spaces allowed between the groups.
     *
     * <p>Deliberately loose about length and grouping, because the mod-97
     * checksum in {@code Ibans} is a far better filter than any regex, and a
     * pattern strict enough to be safe on its own would miss accounts printed
     * in unusual groupings.</p>
     */
    public static ValuePattern iban() {
        return ValuePattern.of(
                "(?<![\\w])([A-Z]{2}[ ]?" + DIGIT + "{2}(?:[ ]?[0-9A-Z]{4}){2,7}[ ]?[0-9A-Z]{0,4})(?![\\w])",
                text -> countRealDigits(text) >= 4);
    }

    // ------------------------------------------------------------ currency

    /** The currency an invoice is denominated in, as printed beside its totals. */
    public static ValuePattern currency() {
        return ValuePattern.of("(?<![\\p{L}])(RON|LEI|EUR|USD|GBP|CHF|MDL)(?![\\p{L}])");
    }

    // ------------------------------------------------------------ VAT rate

    /**
     * The percentage beside a VAT label: the {@code 19} of "TVA 19%".
     *
     * <p>Anchored on the VAT word rather than on the percent sign, so a discount
     * or a payment-term percentage elsewhere on the page is not mistaken for the
     * rate. The caller still checks the number against the rates in force.</p>
     */
    public static ValuePattern vatRate() {
        return ValuePattern.of(
                "T\\.?[ \\t]?V\\.?[ \\t]?A\\.?[ \\t]*(?:de[ \\t]*)?(" + DIGIT + "{1,2})[ \\t]*%",
                ValuePatterns::hasRealDigit);
    }

    // ---------------------------------------------------------- quantities

    /** A count of goods: a small number, with or without decimals. */
    public static ValuePattern quantity() {
        return ValuePattern.of("(?<![\\w.,])(" + DIGIT + "{1,4}(?:[.,]" + DIGIT + "{1,3})?)(?![\\w.,]*\\d)",
                ValuePatterns::hasRealDigit);
    }

    // -------------------------------------------------------- company names

    /** Legal forms that mark a line as a company name rather than an address. */
    public static final Pattern COMPANY_LINE = Pattern.compile(
            "(?:^|\\s|\\.)(?:S\\.?C\\.?|SRL|S\\.R\\.L\\.|SA|S\\.A\\.|PFA|SNC|SCS|SRL-D|I\\.I\\.)(?:\\s|\\.|,|$)",
            Pattern.CASE_INSENSITIVE);

    /** Words that introduce the other party, ending the supplier's block. */
    public static final Pattern BUYER_MARKER = Pattern.compile(
            "\\b(?:Cumparator|Client|Beneficiar|Achizitor|Cumparatorul)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * The first line of the summary block at the foot of the page.
     *
     * <p>"Total fara TVA" is listed first and matched as a whole, because it is
     * the net line and it is the earliest of the three to appear. Anchoring on
     * a bare "Total" would work equally well on most pages and fail on the ones
     * that print a "Total" column heading over the line items.</p>
     */
    public static final Pattern TOTALS_MARKER = Pattern.compile(
            "^[ \\t]*(?:Total[ \\t]*(?:fara[ \\t]*TVA|general|de[ \\t]*plat[a]|de[ \\t]*achitat|factur[a])"
                    + "|Valoare[ \\t]*(?:totala|TVA)|Subtotal|Baza[ \\t]*impozabila)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * The column headings above the goods table.
     *
     * <p>Two headings on one line are required, never one. A single word like
     * "Valoare" appears in the totals block as well, and a heading row that is
     * really a summary line would put the extractor to work on the wrong part
     * of the page.</p>
     */
    public static final Pattern TABLE_HEADING = Pattern.compile(
            "^[^\\r\\n]*\\b(?:Denumire|Descriere|Produs|Serviciu|Articol|Nr\\.?[ \\t]*crt)\\b"
                    + "[^\\r\\n]*\\b(?:Cant|Cantitate|U\\.?M\\.?|Pret|Valoare|Suma)\\b[^\\r\\n]*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /** Words that introduce the supplier's block. */
    public static final Pattern SUPPLIER_MARKER = Pattern.compile(
            "\\b(?:Furnizor|Vanzator|Emitent|Prestator|Furnizorul)\\b",
            Pattern.CASE_INSENSITIVE);

    // ------------------------------------------------------------- helpers

    public static boolean hasRealDigit(String text) {
        return REAL_DIGIT.matcher(text).find();
    }

    public static int countRealDigits(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    public static boolean looksLikeDate(String text) {
        return DATE_SHAPE.matcher(text.trim()).matches();
    }

    /**
     * True when a date is printed anywhere inside {@code text}.
     *
     * <p>Used to keep "Factura nr. … din 08.08.2024" from reporting the date as
     * the invoice number: the candidate may carry a word in front of the date,
     * so testing the whole string for date shape is not enough.</p>
     */
    public static boolean containsDate(String text) {
        return DATE_ANYWHERE.matcher(text).find();
    }

    private ValuePatterns() {
        throw new AssertionError("No instances");
    }
}
