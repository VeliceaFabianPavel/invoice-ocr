package com.invoiceocr.extraction;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one edit a refinement ever needs to make: put these fields back, leave
 * the rest alone.
 *
 * <p>Field order is display order, and a refinement that rebuilt the list would
 * have to preserve it by hand. Doing it here once means a refinement can be
 * written as a decision about values, which is all any of them are.</p>
 */
public final class Refinements {

    /** {@code data} with every field in {@code replacements} substituted in place. */
    public static InvoiceData replaceAll(InvoiceData data, List<ExtractedField> replacements) {
        if (replacements.isEmpty()) {
            return data;
        }
        Map<FieldDefinition, ExtractedField> pending = new LinkedHashMap<>();
        replacements.forEach(field -> pending.put(field.definition(), field));

        List<ExtractedField> merged = new ArrayList<>(data.fields().size() + pending.size());
        for (ExtractedField field : data.fields()) {
            ExtractedField replacement = pending.remove(field.definition());
            merged.add(replacement == null ? field : replacement);
        }
        merged.addAll(pending.values());
        return InvoiceData.of(data.source(), merged, data.lineItems());
    }

    /** {@code data} with one field substituted, or added when the catalog did not carry it. */
    public static InvoiceData replace(InvoiceData data, ExtractedField replacement) {
        return replaceAll(data, List.of(replacement));
    }

    private Refinements() {
        throw new AssertionError("No instances");
    }
}
