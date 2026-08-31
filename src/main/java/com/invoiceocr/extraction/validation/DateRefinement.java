package com.invoiceocr.extraction.validation;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.extraction.InvoiceRefinement;
import com.invoiceocr.extraction.Refinements;
import com.invoiceocr.extraction.text.SearchText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconciles the two dates an invoice carries.
 *
 * <p>Most Romanian invoices never print the date they fall due. What they print
 * instead is the term — "Termen de plata: 30 zile" — which is the same fact
 * expressed as an offset, and an offset plus the issue date is a date. Working
 * it out here means the field is populated on the great majority of invoices
 * rather than the minority that spell it out.</p>
 *
 * <p>The other half is a sanity check that costs nothing. A due date earlier
 * than the issue date is not a payment term, it is a misread: usually the issue
 * date of the previous invoice caught by a stray label. Rather than throw it
 * away, the pair is marked for review, because which of the two dates is wrong
 * is not something this pass can know.</p>
 */
public final class DateRefinement implements InvoiceRefinement {

    private static final String FROM_TERM = "payment-term";
    private static final String INCONSISTENT = "dates-inconsistent";

    /** What a date is worth once the other date says it cannot be right. */
    private static final double INCONSISTENT_CONFIDENCE = 0.30;

    /** Longest term worth believing; anything larger is a misread quantity. */
    private static final int MAXIMUM_TERM_DAYS = 365;

    /** "Termen de plata 30 zile", and the handful of ways it is otherwise written. */
    private static final Pattern PAYMENT_TERM = Pattern.compile(
            "\\b(?:termen(?:ul)?[ \\t]*(?:de[ \\t]*)?plat[a]|scadent[a]|plata[ \\t]*in|payment[ \\t]*terms?)"
                    + "[ \\t]*[:.\\-]?[ \\t]*(\\d{1,3})[ \\t]*(?:zile|days|z\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    public InvoiceData refine(InvoiceData data, SearchText text) {
        Optional<LocalDate> issued = dateOf(data, InvoiceFields.ISSUE_DATE);
        if (issued.isEmpty()) {
            return data;
        }
        Optional<LocalDate> due = dateOf(data, InvoiceFields.DUE_DATE);

        if (due.isEmpty()) {
            return termIn(text)
                    .map(days -> Refinements.replace(data, ExtractedField.of(InvoiceFields.DUE_DATE,
                            Dates.format(issued.get().plusDays(days)),
                            FieldConfidence.DERIVED, FROM_TERM)))
                    .orElse(data);
        }
        if (due.get().isBefore(issued.get())) {
            return doubt(data);
        }
        return data;
    }

    /** The payment term in days, when the page states one that makes sense. */
    private static Optional<Integer> termIn(SearchText text) {
        Matcher matcher = text.matcher(PAYMENT_TERM, text.whole());
        while (matcher.find()) {
            int days = Integer.parseInt(matcher.group(1));
            if (days > 0 && days <= MAXIMUM_TERM_DAYS) {
                return Optional.of(days);
            }
        }
        return Optional.empty();
    }

    /** Lowers both dates rather than choosing between them, which this pass cannot do. */
    private static InvoiceData doubt(InvoiceData data) {
        List<ExtractedField> fields = new ArrayList<>(data.fields().size());
        for (ExtractedField field : data.fields()) {
            boolean isDate = field.definition().equals(InvoiceFields.ISSUE_DATE)
                    || field.definition().equals(InvoiceFields.DUE_DATE);
            fields.add(isDate && field.isPresent()
                    ? field.ratedAt(INCONSISTENT_CONFIDENCE, INCONSISTENT)
                    : field);
        }
        return InvoiceData.of(data.source(), fields, data.lineItems());
    }

    private static Optional<LocalDate> dateOf(InvoiceData data,
                                              com.invoiceocr.domain.FieldDefinition field) {
        return data.valueOf(field).flatMap(Dates::parse);
    }


}
