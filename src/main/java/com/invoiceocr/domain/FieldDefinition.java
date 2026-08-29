package com.invoiceocr.domain;

import java.util.Objects;

/**
 * Describes one piece of information that can be extracted from an invoice.
 *
 * <p>Fields are values, not an enum, so a new field can be introduced by an
 * extension (another rule provider, another catalog) without modifying any
 * existing type.</p>
 *
 * @param key          stable technical identifier, used for lookups and JSON keys
 * @param labelKey     i18n key resolved by a {@code MessageSource} at render time
 * @param displayOrder ascending order used when a report lists the fields
 */
public record FieldDefinition(String key, String labelKey, int displayOrder)
        implements Comparable<FieldDefinition> {

    public FieldDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(labelKey, "labelKey");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Field key must not be blank");
        }
    }

    @Override
    public int compareTo(FieldDefinition other) {
        int byOrder = Integer.compare(displayOrder, other.displayOrder);
        return byOrder != 0 ? byOrder : key.compareTo(other.key);
    }
}
