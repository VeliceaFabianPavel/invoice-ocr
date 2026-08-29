package com.invoiceocr.extraction.text;

import com.invoiceocr.domain.RecognizedText;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The page prepared for searching: the original text, plus a folded copy of the
 * same length that patterns are matched against.
 *
 * <p>Every value handed back is sliced out of the <em>original</em>, so the
 * caller never sees the folding. Matching is therefore insensitive to
 * diacritics and case, while {@code SC LAMBDA SOLUȚII SRL} still comes out
 * spelled the way it was printed.</p>
 */
public final class SearchText {

    private final String original;
    private final String folded;

    private SearchText(String original) {
        this.original = Objects.requireNonNull(original, "original");
        this.folded = TextFolding.fold(original);
    }

    public static SearchText of(String text) {
        return new SearchText(text == null ? "" : text);
    }

    public static SearchText of(RecognizedText text) {
        return new SearchText(Objects.requireNonNull(text, "text").value());
    }

    public String original() {
        return original;
    }

    public String folded() {
        return folded;
    }

    public int length() {
        return original.length();
    }

    public TextRegion whole() {
        return new TextRegion(0, original.length());
    }

    /** The original text between two offsets found in the folded copy. */
    public String slice(int start, int end) {
        return original.substring(Math.max(0, start), Math.min(original.length(), end));
    }

    /**
     * A matcher over the folded text, limited to {@code region}.
     *
     * <p>Bounds are transparent so a look-behind can still see the characters
     * just before the region, and non-anchoring so {@code ^} keeps meaning
     * "start of line" rather than "start of region".</p>
     */
    public Matcher matcher(Pattern pattern, TextRegion region) {
        Matcher matcher = pattern.matcher(folded);
        matcher.region(region.start(), Math.min(region.end(), folded.length()));
        matcher.useTransparentBounds(true);
        matcher.useAnchoringBounds(false);
        return matcher;
    }

    /** Offset just past the end of the line containing {@code offset}. */
    public int endOfLine(int offset) {
        int newline = original.indexOf('\n', Math.min(offset, original.length()));
        return newline < 0 ? original.length() : newline;
    }

    /** Offset at the start of the line containing {@code offset}. */
    public int startOfLine(int offset) {
        int newline = original.lastIndexOf('\n', Math.max(0, Math.min(offset, original.length()) - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    /**
     * Offset {@code lines} line breaks after {@code offset}, or the end of the
     * text. Used to keep a value search near the label that introduced it.
     */
    public int skipLines(int offset, int lines) {
        int at = Math.min(offset, original.length());
        for (int i = 0; i < lines; i++) {
            int newline = original.indexOf('\n', at);
            if (newline < 0) {
                return original.length();
            }
            at = newline + 1;
        }
        return at;
    }
}
