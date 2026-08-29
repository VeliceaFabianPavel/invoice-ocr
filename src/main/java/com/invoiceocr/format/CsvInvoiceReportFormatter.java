package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * One header row of field keys and one row of values, so several exported
 * invoices stack into a spreadsheet by pasting them under each other.
 *
 * <p>Quoting follows RFC 4180 and line endings are CRLF, which is what
 * spreadsheet software expects. A missing field is an empty cell.</p>
 */
public final class CsvInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String NL = "\r\n";
    private static final char SEPARATOR = ',';

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        List<ExtractedField> fields = data.fields();

        StringJoiner header = new StringJoiner(String.valueOf(SEPARATOR));
        StringJoiner values = new StringJoiner(String.valueOf(SEPARATOR));
        for (ExtractedField field : fields) {
            header.add(quote(field.definition().key()));
            values.add(quote(field.valueOr("")));
        }
        return header + NL + values + NL;
    }

    private static String quote(String value) {
        boolean needsQuotes = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
