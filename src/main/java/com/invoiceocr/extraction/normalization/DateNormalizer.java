package com.invoiceocr.extraction.normalization;

/**
 * Normalises a day-first date to {@code dd.MM.yyyy}.
 *
 * <p>Anything that does not parse as a plausible calendar date is returned
 * untouched: showing the operator what OCR actually read beats hiding it behind
 * a silently wrong reformat.</p>
 */
public final class DateNormalizer implements ValueNormalizer {

    private static final int CENTURY = 2000;
    private static final int MAX_MONTH = 12;
    private static final int MAX_DAY = 31;

    @Override
    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        String[] parts = trimmed.split("[./\\-]");
        if (parts.length != 3) {
            return trimmed;
        }
        try {
            int day = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            int year = normalizeYear(Integer.parseInt(parts[2].trim()));
            if (day < 1 || day > MAX_DAY || month < 1 || month > MAX_MONTH) {
                return trimmed;
            }
            return String.format("%02d.%02d.%04d", day, month, year);
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

    private static int normalizeYear(int year) {
        return year < 100 ? CENTURY + year : year;
    }
}
