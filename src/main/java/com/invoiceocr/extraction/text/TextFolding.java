package com.invoiceocr.extraction.text;

/**
 * Folds letters to their plain ASCII base, one character for one character.
 *
 * <p>Length is preserved on purpose. Patterns can then be written in plain
 * ASCII and matched against the folded text, while every offset still points at
 * the same place in the original — so the value handed back keeps its
 * diacritics exactly as recognised.</p>
 *
 * <p>This is what lets a single pattern {@code fara} match {@code fără},
 * {@code FARA} and {@code fǎrǎ} without three alternatives in every rule.</p>
 */
public final class TextFolding {

    private static final String ACCENTED =
            "ăĂâÂàÀáÁäÄãÃ"
          + "șȘşŞśŚ"
          + "țȚţŢ"
          + "îÎïÏìÌíÍ"
          + "éÉèÈêÊëË"
          + "öÖôÔòÒóÓõÕ"
          + "üÜûÛùÙúÚ"
          + "çÇñÑ";

    private static final String PLAIN =
            "aAaAaAaAaAaA"
          + "sSsSsS"
          + "tTtT"
          + "iIiIiIiI"
          + "eEeEeEeE"
          + "oOoOoOoOoO"
          + "uUuUuUuU"
          + "cCnN";

    static {
        if (ACCENTED.length() != PLAIN.length()) {
            throw new IllegalStateException("Folding tables must line up character for character");
        }
    }

    /** Returns {@code text} with accented letters replaced, same length. */
    public static String fold(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = ACCENTED.indexOf(c);
            folded.append(index < 0 ? c : PLAIN.charAt(index));
        }
        return folded.toString();
    }

    private TextFolding() {
        throw new AssertionError("No instances");
    }
}
