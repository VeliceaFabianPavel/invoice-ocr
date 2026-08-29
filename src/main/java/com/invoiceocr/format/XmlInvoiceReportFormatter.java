package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import java.util.Objects;

/**
 * XML for another system to read.
 *
 * <p>Uses the stable field keys rather than translated labels, so the document
 * means the same thing whatever language the interface is running in. A field
 * with no value is present but empty, and marked {@code found="false"}, so a
 * consumer can tell "not on the invoice" from "empty string".</p>
 */
public final class XmlInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String NL = "\n";
    private static final String INDENT = "  ";

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        StringBuilder out = new StringBuilder();

        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(NL)
                .append("<invoice recognized=\"").append(data.recognizedCount())
                .append("\" fields=\"").append(data.fields().size()).append("\">").append(NL);

        for (ExtractedField field : data.fields()) {
            out.append(INDENT)
                    .append("<field key=\"").append(escapeAttribute(field.definition().key()))
                    .append("\" found=\"").append(field.isPresent()).append("\">")
                    .append(escapeText(field.valueOr("")))
                    .append("</field>").append(NL);
        }

        out.append("</invoice>").append(NL);
        return out.toString();
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }
}
