package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a field with a regular expression and one capturing group.
 *
 * <p>Matching runs against the folded text, so a pattern written in plain ASCII
 * also matches accented text, while the value returned is sliced out of the
 * original and keeps its accents.</p>
 */
public final class RegexFieldExtractor implements FieldExtractor {

    private static final int DEFAULT_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;
    private static final int DEFAULT_GROUP = 1;

    private final Pattern pattern;
    private final int group;

    public RegexFieldExtractor(Pattern pattern, int group) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        if (group < 0) {
            throw new IllegalArgumentException("group must not be negative");
        }
        this.group = group;
    }

    /** Compiles {@code regex} case-insensitively and captures group 1. */
    public static RegexFieldExtractor of(String regex) {
        return new RegexFieldExtractor(Pattern.compile(regex, DEFAULT_FLAGS), DEFAULT_GROUP);
    }

    @Override
    public Optional<String> extract(SearchText text, TextRegion region) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = text.matcher(pattern, region);
        if (!matcher.find() || matcher.groupCount() < group || matcher.start(group) < 0) {
            return Optional.empty();
        }
        String value = text.slice(matcher.start(group), matcher.end(group));
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    @Override
    public String toString() {
        return "RegexFieldExtractor[" + pattern.pattern() + "]";
    }
}
