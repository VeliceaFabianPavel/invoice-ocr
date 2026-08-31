package com.invoiceocr.extraction.items;

import com.invoiceocr.domain.LineItem;
import com.invoiceocr.extraction.text.SearchText;
import com.invoiceocr.extraction.text.TextRegion;
import java.util.List;

/**
 * Reads the rows of the goods-and-services table.
 *
 * <p>Separate from {@link com.invoiceocr.extraction.FieldExtractor} because the
 * problem is a different shape: a field is one value somewhere on the page,
 * whereas a table is an unknown number of rows in a known place, each with the
 * same internal structure. One interface pretending to serve both would serve
 * neither well.</p>
 */
@FunctionalInterface
public interface LineItemExtractor {

    /** @return the rows found inside {@code region}, in printed order; never null */
    List<LineItem> extract(SearchText text, TextRegion region);

    /** The extractor that finds nothing, for a configuration with the table turned off. */
    static LineItemExtractor none() {
        return (text, region) -> List.of();
    }
}
