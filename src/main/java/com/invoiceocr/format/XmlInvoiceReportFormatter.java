package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.LineItem;
import java.util.Locale;
import java.util.Objects;

/**
 * XML for another system to read.
 *
 * <p>Uses the stable field keys rather than translated labels, so the document
 * means the same thing whatever language the interface is running in. A field
 * with no value is present but empty, and marked {@code found="false"}, so a
 * consumer can tell "not on the invoice" from "empty string".</p>
 *
 * <p>Everything 1.2 added arrives as attributes and one new element, so a
 * consumer written against 1.1 keeps working: {@code confidence} and
 * {@code strategy} say how much a value is worth and where it came from, and
 * {@code <lineItems>} carries the goods table when one was read.</p>
 */
public final class XmlInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String NL = "\n";
    private static final String INDENT = "  ";
    private static final String NESTED = INDENT + INDENT;

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        StringBuilder out = new StringBuilder();

        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(NL)
                .append("<invoice recognized=\"").append(data.recognizedCount())
                .append("\" fields=\"").append(data.fields().size())
                .append("\" averageConfidence=\"").append(number(data.averageConfidence()))
                .append("\">").append(NL);

        for (ExtractedField field : data.fields()) {
            out.append(INDENT)
                    .append("<field key=\"").append(escapeAttribute(field.definition().key()))
                    .append("\" found=\"").append(field.isPresent()).append('"');
            if (field.isPresent()) {
                out.append(" confidence=\"").append(number(field.confidence()))
                        .append("\" strategy=\"").append(escapeAttribute(field.strategy()))
                        .append("\" review=\"").append(field.needsReview()).append('"');
            }
            out.append('>').append(escapeText(field.valueOr("")))
                    .append("</field>").append(NL);
        }

        if (data.hasLineItems()) {
            out.append(INDENT).append("<lineItems count=\"").append(data.lineItems().size())
                    .append("\">").append(NL);
            for (LineItem item : data.lineItems()) {
                out.append(NESTED).append("<item");
                item.quantity().ifPresent(quantity ->
                        out.append(" quantity=\"").append(escapeAttribute(quantity)).append('"'));
                item.unitPrice().ifPresent(price ->
                        out.append(" unitPrice=\"").append(escapeAttribute(price)).append('"'));
                out.append(" value=\"").append(escapeAttribute(item.lineTotal())).append("\">")
                        .append(escapeText(item.description()))
                        .append("</item>").append(NL);
            }
            out.append(INDENT).append("</lineItems>").append(NL);
        }

        out.append("</invoice>").append(NL);
        return out.toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }
}
