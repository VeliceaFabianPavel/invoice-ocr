package com.invoiceocr.extraction;

import com.invoiceocr.domain.FieldConfidence;
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
 *
 * <p>A find inside the right block is worth more than the same find anywhere on
 * the page, because the region is itself a piece of evidence: it is the only
 * thing that distinguishes two fiscal codes printed in identical shape. The
 * delegate's rating is raised for that, never past the band a labelled read
 * would have earned.</p>
 */
public final class RegionScopedExtractor implements FieldExtractor {

    /** How much being in the right block is worth. */
    private static final double SCOPE_BONUS = 1.20;

    private final FieldExtractor delegate;
    private final Function<SearchText, TextRegion> scope;
    private final String scopeName;

    public RegionScopedExtractor(FieldExtractor delegate,
                                 Function<SearchText, TextRegion> scope,
                                 String scopeName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.scopeName = Objects.requireNonNull(scopeName, "scopeName");
    }

    /** Confines {@code delegate} to the supplier's block. */
    public static RegionScopedExtractor inSupplierBlock(FieldExtractor delegate) {
        return new RegionScopedExtractor(delegate, DocumentRegions::supplier, "supplier-block");
    }

    /** Confines {@code delegate} to the buyer's block. */
    public static RegionScopedExtractor inBuyerBlock(FieldExtractor delegate) {
        return new RegionScopedExtractor(delegate, DocumentRegions::buyer, "buyer-block");
    }

    /** Confines {@code delegate} to the totals block at the foot of the page. */
    public static RegionScopedExtractor inTotalsBlock(FieldExtractor delegate) {
        return new RegionScopedExtractor(delegate, DocumentRegions::totals, "totals-block");
    }

    @Override
    public Optional<Extraction> extract(SearchText text, TextRegion region) {
        TextRegion scoped = scope.apply(text);
        TextRegion intersection = new TextRegion(
                Math.max(region.start(), scoped.start()),
                Math.max(Math.max(region.start(), scoped.start()), Math.min(region.end(), scoped.end())));
        if (intersection.isEmpty()) {
            return Optional.empty();
        }
        return delegate.extract(text, intersection)
                .map(found -> found.ratedAt(
                        Math.min(FieldConfidence.LABELLED, found.confidence() * SCOPE_BONUS))
                        .via(scopeName));
    }
}
