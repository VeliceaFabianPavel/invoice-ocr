package com.invoiceocr.extraction.text;

/**
 * Puts back the digits Tesseract turned into letters.
 *
 * <p>Two modes, because the right answer depends on the field.</p>
 *
 * <ul>
 *   <li>{@link #repair} fixes every confusable character, for values that are
 *       entirely numeric: dates, amounts, the digits of a fiscal code.</li>
 *   <li>{@link #repairNumericRuns} fixes only runs that are made up entirely of
 *       digits and confusables <em>and</em> already contain a real digit. That
 *       turns {@code ZT-OO91} into {@code ZT-0091} while leaving {@code AB123}
 *       and a series like {@code SB} alone — repairing those would invent
 *       {@code A8123} and {@code 58}.</li>
 * </ul>
 */
public final class OcrDigits {

    /** Repairs every confusable character. For values that are entirely numeric. */
    public static String repair(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(toDigit(text.charAt(i)));
        }
        return out.toString();
    }

    /** Repairs only runs that are wholly digit-shaped and hold at least one real digit. */
    public static String repairNumericRuns(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            if (!Character.isLetterOrDigit(text.charAt(at))) {
                out.append(text.charAt(at));
                at++;
                continue;
            }
            int end = at;
            while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
                end++;
            }
            String run = text.substring(at, end);
            out.append(shouldRepair(run) ? repair(run) : run);
            at = end;
        }
        return out.toString();
    }

    private static boolean shouldRepair(String run) {
        boolean sawDigit = false;
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            if (Character.isDigit(c)) {
                sawDigit = true;
            } else if (toDigit(c) == c) {
                return false;   // a letter that is not a known confusion: leave the run alone
            }
        }
        return sawDigit;
    }

    private static char toDigit(char c) {
        return switch (c) {
            case 'O', 'o' -> '0';
            case 'l', 'I', 'i', '|' -> '1';
            case 'Z', 'z' -> '2';
            case 'S', 's' -> '5';
            case 'B', 'b' -> '8';
            default -> c;
        };
    }

    private OcrDigits() {
        throw new AssertionError("No instances");
    }
}
