package com.invoiceocr.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One row of the invoice's goods-and-services table.
 *
 * <p>Everything except the description is optional, because the columns an
 * invoice prints vary: some show quantity, unit price and value, some only a
 * value, and a badly scanned row may lose a column to OCR. A row is kept as
 * soon as it has a description and a value — half a row is more use than none,
 * and the missing cells are reported as missing rather than as zero.</p>
 *
 * @param description what was sold, as printed
 * @param quantity    how many, canonicalised; empty when the column is absent
 * @param unitPrice   price for one, canonicalised; empty when the column is absent
 * @param lineTotal   what the row comes to, canonicalised
 */
public record LineItem(String description, Optional<String> quantity,
                       Optional<String> unitPrice, String lineTotal) {

    public LineItem {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(lineTotal, "lineTotal");
        if (description.isBlank()) {
            throw new IllegalArgumentException("A line item must have a description");
        }
    }

    public static LineItem of(String description, String quantity, String unitPrice, String lineTotal) {
        return new LineItem(description.trim(),
                optional(quantity),
                optional(unitPrice),
                lineTotal == null ? "" : lineTotal.trim());
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(v -> !v.isEmpty());
    }

    public String quantityOr(String fallback) {
        return quantity.orElse(fallback);
    }

    public String unitPriceOr(String fallback) {
        return unitPrice.orElse(fallback);
    }
}
