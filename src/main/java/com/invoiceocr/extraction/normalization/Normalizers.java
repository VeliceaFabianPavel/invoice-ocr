package com.invoiceocr.extraction.normalization;

/**
 * Ready-made normaliser chains for the common invoice value shapes.
 *
 * <p>Each chain ends where the value is presentable, and each begins by undoing
 * the OCR damage the patterns were written to tolerate.</p>
 */
public final class Normalizers {

    /** Whitespace collapsed and edge punctuation removed: the default for free text. */
    public static ValueNormalizer text() {
        return NormalizerChain.of(new WhitespaceNormalizer(), new TrailingPunctuationNormalizer());
    }

    /** Free text, uppercased. */
    public static ValueNormalizer code() {
        return text().andThen(new UpperCaseNormalizer());
    }

    /** Invoice numbers: digits repaired inside numeric runs, connecting words dropped. */
    public static ValueNormalizer documentNumber() {
        return text()
                .andThen(new DigitRepairNormalizer(DigitRepairNormalizer.Scope.NUMERIC_RUNS))
                .andThen(new DocumentNumberNormalizer());
    }

    /** Monetary amounts, rendered as {@code 1234.56}. */
    public static ValueNormalizer amount() {
        return text()
                .andThen(new DigitRepairNormalizer(DigitRepairNormalizer.Scope.WHOLE_VALUE))
                .andThen(new AmountNormalizer());
    }

    /** Day-first dates, rendered as {@code dd.MM.yyyy}. */
    public static ValueNormalizer date() {
        return text()
                .andThen(new DigitRepairNormalizer(DigitRepairNormalizer.Scope.WHOLE_VALUE))
                .andThen(new DateNormalizer());
    }

    /** Romanian fiscal codes, rendered as {@code RO12345678}. */
    public static ValueNormalizer fiscalCode() {
        return text().andThen(new FiscalCodeNormalizer());
    }

    private Normalizers() {
        throw new AssertionError("No instances");
    }
}
