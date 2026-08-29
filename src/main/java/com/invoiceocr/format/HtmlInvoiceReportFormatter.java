package com.invoiceocr.format;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.util.Objects;

/**
 * A self-contained HTML page: no external stylesheet, no script, so the file
 * survives being e-mailed or archived on its own.
 */
public final class HtmlInvoiceReportFormatter implements InvoiceReportFormatter {

    private static final String NL = "\n";

    private final MessageSource messages;

    public HtmlInvoiceReportFormatter(MessageSource messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String format(InvoiceData data) {
        Objects.requireNonNull(data, "data");
        String title = messages.get(MessageKeys.REPORT_HEADER);
        StringBuilder out = new StringBuilder();

        out.append("<!doctype html>").append(NL)
                .append("<html lang=\"en\">").append(NL)
                .append("<head>").append(NL)
                .append("<meta charset=\"utf-8\">").append(NL)
                .append("<title>").append(escape(title)).append("</title>").append(NL)
                .append("<style>").append(NL).append(STYLE).append(NL).append("</style>").append(NL)
                .append("</head>").append(NL)
                .append("<body>").append(NL)
                .append("<h1>").append(escape(title)).append("</h1>").append(NL)
                .append("<table>").append(NL)
                .append("<thead><tr><th>").append(escape(messages.get(MessageKeys.REPORT_COLUMN_FIELD)))
                .append("</th><th>").append(escape(messages.get(MessageKeys.REPORT_COLUMN_VALUE)))
                .append("</th></tr></thead>").append(NL)
                .append("<tbody>").append(NL);

        String missing = messages.get(MessageKeys.REPORT_MISSING_VALUE);
        for (ExtractedField field : data.fields()) {
            String value = field.valueOr(missing);
            out.append("<tr><th scope=\"row\">")
                    .append(escape(messages.get(field.definition().labelKey())))
                    .append("</th><td")
                    .append(field.isPresent() ? "" : " class=\"missing\"")
                    .append(">").append(escape(value)).append("</td></tr>").append(NL);
        }

        out.append("</tbody>").append(NL).append("</table>").append(NL)
                .append("<p class=\"summary\">")
                .append(escape(messages.get(MessageKeys.REPORT_FOOTER,
                        data.recognizedCount(), data.fields().size())))
                .append("</p>").append(NL);

        if (!data.source().isBlank()) {
            out.append("<h2>").append(escape(messages.get(MessageKeys.REPORT_RAW_TEXT))).append("</h2>").append(NL)
                    .append("<pre>").append(escape(data.source().value())).append("</pre>").append(NL);
        }

        out.append("</body>").append(NL).append("</html>").append(NL);
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String STYLE = """
            :root { color-scheme: light dark; }
            body {
              margin: 2rem auto; max-width: 46rem; padding: 0 1.2rem;
              font-family: "Segoe UI", system-ui, sans-serif; line-height: 1.6;
              background: #f7f8f5; color: #16211c;
            }
            h1 { font-size: 1.6rem; margin: 0 0 1.2rem; }
            h2 { font-size: 1.1rem; margin: 2rem 0 .6rem; }
            table { border-collapse: collapse; width: 100%; }
            th, td { text-align: left; padding: .5rem .7rem; border-bottom: 1px solid #d6ded7; }
            thead th {
              font-size: .75rem; letter-spacing: .1em; text-transform: uppercase; color: #55645c;
            }
            tbody th { width: 40%; font-weight: 600; }
            tbody tr:nth-child(even) { background: #ecf1e9; }
            td.missing { color: #9a3324; }
            .summary { color: #55645c; font-size: .9rem; }
            pre {
              white-space: pre-wrap; word-break: break-word;
              background: #fff; border: 1px solid #d6ded7; border-left: 3px solid #1d6a4f;
              border-radius: 4px; padding: 1rem; font-size: .85rem;
            }
            @media (prefers-color-scheme: dark) {
              body { background: #101410; color: #e5ebe3; }
              th, td { border-bottom-color: #2a332b; }
              thead th { color: #a2aea5; }
              tbody tr:nth-child(even) { background: #1c231d; }
              td.missing { color: #e38c78; }
              .summary { color: #a2aea5; }
              pre { background: #161b16; border-color: #2a332b; border-left-color: #63c495; }
            }
            @media print { body { background: #fff; color: #000; } }
            """;
}
