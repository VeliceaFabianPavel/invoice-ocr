package com.invoiceocr.extraction.text;

/**
 * A half-open span of the page, used to keep a search inside one part of it.
 *
 * <p>Scoping matters more than it looks: the buyer's fiscal code is printed in
 * exactly the same shape as the supplier's, so the only thing that tells them
 * apart is which block they sit in.</p>
 */
public record TextRegion(int start, int end) {

    public TextRegion {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid region: " + start + ".." + end);
        }
    }

    public int length() {
        return end - start;
    }

    public boolean isEmpty() {
        return end == start;
    }

    /** This region narrowed to begin at {@code from}, never past its own end. */
    public TextRegion from(int from) {
        return new TextRegion(Math.min(Math.max(from, start), end), end);
    }

    /** This region cut short at {@code to}, never before its own start. */
    public TextRegion to(int to) {
        return new TextRegion(start, Math.max(Math.min(to, end), start));
    }
}
