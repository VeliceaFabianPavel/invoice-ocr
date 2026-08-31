package com.invoiceocr.extraction.validation;

import java.math.BigInteger;
import java.util.Locale;

/**
 * The mod-97 checksum every IBAN carries (ISO 13616).
 *
 * <p>Same idea as the fiscal code, and even stronger: the two check digits after
 * the country code are chosen so that the whole account number, rotated and read
 * as one long integer, leaves a remainder of 1 when divided by 97. A single
 * mistyped or misread character fails it.</p>
 *
 * <p>An invoice's bank account is the one field where a plausible-looking wrong
 * answer does real damage, so the rule that reads it rejects anything the
 * checksum does not accept.</p>
 */
public final class Ibans {

    private static final int MINIMUM_LENGTH = 15;
    private static final int MAXIMUM_LENGTH = 34;
    private static final int ROMANIAN_LENGTH = 24;
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    /** True when {@code iban} passes the mod-97 check and has a sane length. */
    public static boolean isValid(String iban) {
        String compact = compact(iban);
        if (compact.length() < MINIMUM_LENGTH || compact.length() > MAXIMUM_LENGTH) {
            return false;
        }
        if (!Character.isLetter(compact.charAt(0)) || !Character.isLetter(compact.charAt(1))) {
            return false;
        }
        if (compact.startsWith("RO") && compact.length() != ROMANIAN_LENGTH) {
            return false;
        }
        String rotated = compact.substring(4) + compact.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rotated.length() * 2);
        for (char c : rotated.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else {
                return false;
            }
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** Upper case with every space and separator removed, the form the checksum is defined on. */
    public static String compact(String iban) {
        if (iban == null) {
            return "";
        }
        return iban.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** Groups of four, which is how a bank account is meant to be read back. */
    public static String format(String iban) {
        String compact = compact(iban);
        StringBuilder grouped = new StringBuilder(compact.length() + compact.length() / 4);
        for (int i = 0; i < compact.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                grouped.append(' ');
            }
            grouped.append(compact.charAt(i));
        }
        return grouped.toString();
    }

    /**
     * A veto rather than a warning: an account that does not verify is never
     * reported, at any confidence.
     *
     * <p>The pattern that finds an account is loose on purpose, so the checksum
     * is not confirming the match - it is the match. And of all the fields on an
     * invoice, this is the one where a plausible wrong answer costs money.</p>
     */
    public static ValueCheck check() {
        return value -> isValid(value) ? Verdict.PROVEN : Verdict.IMPOSSIBLE;
    }

    private Ibans() {
        throw new AssertionError("No instances");
    }
}
