package com.invoiceocr.extraction;

import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Runs another extractor over part of the page instead of all of it.
 *
 * <p>Scoping is what stops the buyer's fiscal code being reported as the
 * supplier's. It is a decorator rather than a parameter on every extractor, so
 * any strategy — labelled, shape-based or company-name — can be confined
 * without knowing that regions exist.</p>
 */
public final class RegionScopedExtractor implements FieldExtractor {

    private final FieldExtractor delegate;
    private final Function<SearchText, TextRegion> scope;

    public RegionScopedExtractor(FieldExtractor delegate, Function<SearchText, TextRegion> scope) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /** Confines {@code delegate} to the supplier's block. */
    public static RegionScopedExtractor inSupplierBlock(FieldExtractor delegate) {
        return new RegionScopedExtractor(delegate, DocumentRegions::supplier);
    }

    @Override
    public Optional<String> extract(SearchText text, TextRegion region) {
        TextRegion scoped = scope.apply(text);
        TextRegion intersection = new TextRegion(
                Math.max(region.start(), scoped.start()),
                Math.max(Math.max(region.start(), scoped.start()), Math.min(region.end(), scoped.end())));
        if (intersection.isEmpty()) {
            return Optional.empty();
        }
        return delegate.extract(text, intersection);
    }
}
