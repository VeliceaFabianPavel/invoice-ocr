package com.invoiceocr.extraction;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.text.SearchText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies every rule from a provider, at most once per field.
 *
 * <p>Fields whose rules all miss are reported as present-but-empty, so the view
 * can show them as unknown rather than pretending they do not exist.</p>
 */
public final class RuleBasedInvoiceParser implements InvoiceParser {

    private final ExtractionRuleProvider ruleProvider;

    public RuleBasedInvoiceParser(ExtractionRuleProvider ruleProvider) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider");
    }

    @Override
    public InvoiceData parse(RecognizedText text) {
        Objects.requireNonNull(text, "text");

        SearchText searchText = SearchText.of(text);
        List<ExtractedField> extracted = new ArrayList<>();
        Set<String> resolvedKeys = new LinkedHashSet<>();

        for (ExtractionRule rule : ruleProvider.rules()) {
            String key = rule.field().key();
            if (resolvedKeys.contains(key)) {
                continue;
            }
            ExtractedField field = apply(rule, searchText);
            if (field.isPresent()) {
                resolvedKeys.add(key);
                extracted.removeIf(existing -> existing.definition().key().equals(key));
                extracted.add(field);
            } else if (extracted.stream().noneMatch(existing -> existing.definition().key().equals(key))) {
                extracted.add(field);
            }
        }
        return InvoiceData.of(text, extracted);
    }

    private static ExtractedField apply(ExtractionRule rule, SearchText text) {
        return rule.extractor().extract(text)
                .map(rule.normalizer()::normalize)
                .filter(value -> !value.isBlank())
                .map(value -> ExtractedField.of(rule.field(), value))
                .orElseGet(() -> ExtractedField.missing(rule.field()));
    }
}
