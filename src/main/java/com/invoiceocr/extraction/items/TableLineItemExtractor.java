package com.invoiceocr.extraction.items;

import com.invoiceocr.domain.LineItem;
import com.invoiceocr.extraction.text.Amounts;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import com.invoiceocr.extraction.text.ValuePattern;
import com.invoiceocr.extraction.text.ValuePatterns;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a goods table one line at a time, using the numbers on each line to work
 * out what its columns are.
 *
 * <p>There is no way to know in advance which columns an invoice prints. Some
 * show quantity, unit price and value; some only a value; some slip a VAT rate
 * or a unit of measure in between. So the row is read from the right, where the
 * meaning is stable: the last number on a line is what the row comes to, and
 * everything before the first number is what was sold.</p>
 *
 * <pre>
 *   Transport marfa            1   840,00      840,00
 *   ^ description              ^ qty  ^ unit    ^ line total
 * </pre>
 *
 * <p>Reading from the right is what makes this survive a missing column. A row
 * that lost its unit price to a smudge still has a description and a total, and
 * half a row recorded is better than a row dropped.</p>
 */
public final class TableLineItemExtractor implements LineItemExtractor {

    /** Shorter than this and the text before the numbers is a stray mark, not a name. */
    private static final int MINIMUM_DESCRIPTION = 3;

    /** A row with more numbers than this is a ruler or a misread block, not an item. */
    private static final int MAXIMUM_NUMBERS = 8;

    /** Longest run of digits still readable as a "Nr. crt" row number. */
    private static final int MAXIMUM_ROW_INDEX_DIGITS = 3;

    /** Typographic rules drawn with dashes, underscores or equals signs. */
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^[\\s\\-_=|+.]*$");

    /**
     * Words that mean the line is a summary, not an item.
     *
     * <p>The items region normally stops before the totals block, but a table
     * that prints a subtotal in the middle of itself would otherwise contribute
     * a row that is really a sum of the rows above it.</p>
     */
    private static final Pattern NOT_AN_ITEM = Pattern.compile(
            "^[^\\r\\n]{0,40}?\\b(?:total|subtotal|tva|de[ \\t]*plat[a]|baza[ \\t]*impozabila"
                    + "|valoare[ \\t]*totala|rest[ \\t]*de[ \\t]*plata)\\b",
            Pattern.CASE_INSENSITIVE);

    /** A row must have at least one digit in its description, or none at all — never only punctuation. */
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    private final ValuePattern amount = ValuePatterns.amount();

    @Override
    public List<LineItem> extract(SearchText text, TextRegion region) {
        List<LineItem> items = new ArrayList<>();
        int at = region.start();

        while (at < region.end()) {
            int end = Math.min(text.endOfLine(at), region.end());
            if (end > at) {
                readRow(text, new TextRegion(at, end)).ifPresent(items::add);
            }
            at = end + 1;
        }
        return List.copyOf(items);
    }

    /** One line, or empty when it is a rule, a heading, a summary or simply not a row. */
    private java.util.Optional<LineItem> readRow(SearchText text, TextRegion line) {
        String folded = text.folded().substring(line.start(), line.end());
        if (SEPARATOR_LINE.matcher(folded).matches() || NOT_AN_ITEM.matcher(folded).find()) {
            return java.util.Optional.empty();
        }

        List<ValuePattern.Found> numbers = amount.allIn(text, line);
        if (numbers.isEmpty() || numbers.size() > MAXIMUM_NUMBERS) {
            return java.util.Optional.empty();
        }

        List<ValuePattern.Found> columns = withoutRowIndex(text, line, numbers);
        int describedFrom = columns.size() == numbers.size()
                ? line.start()
                : numbers.get(0).end();
        String description = text.slice(describedFrom, columns.get(0).start()).trim();
        description = description.replaceAll("[\\s|]+$", "").replaceAll("^[\\s|]+", "");
        if (description.length() < MINIMUM_DESCRIPTION || !HAS_LETTER.matcher(description).find()) {
            return java.util.Optional.empty();
        }

        String lineTotal = Amounts.normalize(columns.get(columns.size() - 1).value());
        return java.util.Optional.of(
                LineItem.of(description, quantityOf(columns), unitPriceOf(columns), lineTotal));
    }

    /**
     * Drops a leading row number, so the description starts where it should.
     *
     * <p>A "Nr. crt" column is on most Romanian invoices, and it puts a number
     * before the name of the goods — which leaves nothing in front of the first
     * figure and loses the row entirely. It is only dropped when it plainly is
     * one: a small whole number at the very start of the line, with letters
     * after it and at least one figure still to come.</p>
     */
    private static List<ValuePattern.Found> withoutRowIndex(SearchText text, TextRegion line,
                                                            List<ValuePattern.Found> numbers) {
        if (numbers.size() < 2) {
            return numbers;
        }
        ValuePattern.Found first = numbers.get(0);
        boolean atLineStart = text.slice(line.start(), first.start()).isBlank();
        boolean looksLikeIndex = first.value().length() <= MAXIMUM_ROW_INDEX_DIGITS
                && first.value().chars().allMatch(Character::isDigit);
        return atLineStart && looksLikeIndex ? numbers.subList(1, numbers.size()) : numbers;
    }

    /**
     * The quantity, when the row prints one.
     *
     * <p>With three or more numbers the first is the count. With exactly two the
     * row is ambiguous — it is either a quantity and a value, or a unit price and
     * a value — and the decimals decide: a count is normally written whole, a
     * price is not.</p>
     */
    private static String quantityOf(List<ValuePattern.Found> numbers) {
        if (numbers.size() >= 3) {
            return Amounts.normalize(numbers.get(0).value());
        }
        if (numbers.size() == 2 && !hasDecimals(numbers.get(0).value())) {
            return Amounts.normalize(numbers.get(0).value());
        }
        return null;
    }

    private static String unitPriceOf(List<ValuePattern.Found> numbers) {
        if (numbers.size() >= 3) {
            return Amounts.normalize(numbers.get(numbers.size() - 2).value());
        }
        if (numbers.size() == 2 && hasDecimals(numbers.get(0).value())) {
            return Amounts.normalize(numbers.get(0).value());
        }
        return null;
    }

    private static boolean hasDecimals(String printed) {
        String normalized = Amounts.normalize(printed);
        return normalized.contains(".");
    }
}
