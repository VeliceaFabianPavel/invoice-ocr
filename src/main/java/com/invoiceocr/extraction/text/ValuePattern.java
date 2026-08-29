package com.invoiceocr.extraction.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The shape of a value, together with a check that the text found really is
 * one.
 *
 * <p>A regular expression alone is too permissive here: an amount pattern loose
 * enough to survive OCR damage ({@code l.428,OO}) also matches ordinary words.
 * Pairing the pattern with a predicate keeps the pattern simple and the result
 * trustworthy, and lets a search skip a bad candidate and carry on to the next
 * one instead of failing.</p>
 */
public final class ValuePattern {

    /** A match: the original text, and where it was found. */
    public record Found(String value, int start, int end) { }

    private final Pattern pattern;
    private final int[] groups;
    private final String joiner;
    private final Predicate<String> valid;

    private ValuePattern(Pattern pattern, int[] groups, String joiner, Predicate<String> valid) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.groups = groups.clone();
        this.joiner = Objects.requireNonNull(joiner, "joiner");
        this.valid = Objects.requireNonNull(valid, "valid");
    }

    public static ValuePattern of(String regex) {
        return of(regex, value -> true);
    }

    public static ValuePattern of(String regex, Predicate<String> valid) {
        Pattern compiled = compile(regex);
        int[] groups = compiled.matcher("").groupCount() >= 1 ? new int[] { 1 } : new int[] { 0 };
        return new ValuePattern(compiled, groups, "", valid);
    }

    /** Joins several capture groups, for values printed in two pieces ("Seria AB nr 1024"). */
    public static ValuePattern joining(String regex, String joiner, int... groups) {
        return new ValuePattern(compile(regex), groups, joiner, value -> true);
    }

    public ValuePattern requiring(Predicate<String> extra) {
        return new ValuePattern(pattern, groups, joiner, valid.and(extra));
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    /** The first valid value inside {@code region}, taken from the original text. */
    public Optional<Found> firstIn(SearchText text, TextRegion region) {
        Matcher matcher = text.matcher(pattern, region);
        while (matcher.find()) {
            Found found = extract(text, matcher);
            if (found != null && valid.test(found.value())) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    /** Every valid value inside {@code region}, in document order. */
    public List<Found> allIn(SearchText text, TextRegion region) {
        List<Found> all = new ArrayList<>();
        Matcher matcher = text.matcher(pattern, region);
        while (matcher.find()) {
            Found found = extract(text, matcher);
            if (found != null && valid.test(found.value())) {
                all.add(found);
            }
        }
        return all;
    }

    private Found extract(SearchText text, Matcher matcher) {
        if (groups.length == 1) {
            int group = groups[0];
            if (matcher.start(group) < 0) {
                return null;
            }
            return new Found(text.slice(matcher.start(group), matcher.end(group)),
                    matcher.start(group), matcher.end(group));
        }
        List<String> parts = new ArrayList<>(groups.length);
        int start = Integer.MAX_VALUE;
        int end = -1;
        for (int group : groups) {
            if (matcher.start(group) < 0) {
                continue;
            }
            parts.add(text.slice(matcher.start(group), matcher.end(group)));
            start = Math.min(start, matcher.start(group));
            end = Math.max(end, matcher.end(group));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return new Found(parts.stream().collect(Collectors.joining(joiner)), start, end);
    }
}
