package com.invoiceocr.extraction;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.text.SearchText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies every rule from a provider, at most once per field, then lets the
 * refinements look at the result as a whole.
 *
 * <p>Where 1.1 took the first answer each rule produced and stopped, this asks
 * every rung of the ladder and then <em>chooses</em>. Order still leads — the
 * rungs are written most specific first, and that ordering is a judgement no
 * scoring should overrule — but a candidate the rule can actually verify now
 * outranks the ones before it. A fiscal code whose control digit adds up beats
 * one found by a better-placed strategy that does not, because a check of the
 * value itself is better evidence than an argument from where it sat.</p>
 *
 * <p>A candidate that fails its check is not thrown away. It is discounted, and
 * kept as the answer only when nothing better exists — a total the user can
 * correct is more use than a field left blank, as long as it is honestly marked
 * as doubtful.</p>
 *
 * <p>Fields whose rules all miss are reported as present-but-empty, so the view
 * can show them as unknown rather than pretending they do not exist.</p>
 */
public final class RuleBasedInvoiceParser implements InvoiceParser {

    /** What a value is worth once its own check has said it cannot be right. */
    private static final double DOUBTFUL_PENALTY = 0.5;

    private final ExtractionRuleProvider ruleProvider;
    private final List<InvoiceRefinement> refinements;

    public RuleBasedInvoiceParser(ExtractionRuleProvider ruleProvider) {
        this(ruleProvider, List.of());
    }

    public RuleBasedInvoiceParser(ExtractionRuleProvider ruleProvider,
                                  List<InvoiceRefinement> refinements) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider");
        this.refinements = List.copyOf(Objects.requireNonNull(refinements, "refinements"));
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

        InvoiceData data = InvoiceData.of(text, extracted);
        for (InvoiceRefinement refinement : refinements) {
            data = refinement.refine(data, searchText);
        }
        return data;
    }

    /**
     * The best answer this rule can give, or a missing field when it has none.
     *
     * <p>Order still decides, as it always has: the rungs are written from the
     * most specific strategy down to the most speculative, and a later one is
     * never preferred merely because the strategy it used tends to score well.
     * The single thing that can reorder them is proof. A candidate whose control
     * digit adds up is taken over every unproven one before it, because a check
     * of the value itself outranks any argument from where it was found.</p>
     *
     * <p>So: the first proven candidate wins; failing that, the first unproven
     * one; failing that, the first doubtful one, kept at half its rating so it is
     * reported and flagged rather than lost. A candidate the check calls
     * impossible is never reported at all.</p>
     *
     * <p>Every rung is normalised before it is judged, because a check is defined
     * on the canonical value, not on what the page happened to print: a control
     * digit means nothing until the OCR confusions have been repaired and the
     * separators stripped.</p>
     */
    private static ExtractedField apply(ExtractionRule rule, SearchText text) {
        ExtractedField firstUnproven = null;
        ExtractedField firstDoubtful = null;

        for (Extraction candidate : rule.extractor().alternatives(text, text.whole())) {
            String value = rule.normalizer().normalize(candidate.value());
            if (value == null || value.isBlank()) {
                continue;
            }
            switch (rule.check().check(value)) {
                case PROVEN -> {
                    return ExtractedField.of(rule.field(), value,
                            FieldConfidence.VERIFIED, candidate.strategy() + "+checked");
                }
                case UNPROVEN -> {
                    if (firstUnproven == null) {
                        firstUnproven = ExtractedField.of(rule.field(), value,
                                candidate.confidence(), candidate.strategy());
                    }
                }
                case DOUBTFUL -> {
                    if (firstDoubtful == null) {
                        firstDoubtful = ExtractedField.of(rule.field(), value,
                                candidate.confidence() * DOUBTFUL_PENALTY,
                                candidate.strategy() + "+unchecked");
                    }
                }
                case IMPOSSIBLE -> {
                    // Not a value of this kind. The next rung gets its turn.
                }
            }
        }
        if (firstUnproven != null) {
            return firstUnproven;
        }
        return firstDoubtful != null ? firstDoubtful : ExtractedField.missing(rule.field());
    }
}
