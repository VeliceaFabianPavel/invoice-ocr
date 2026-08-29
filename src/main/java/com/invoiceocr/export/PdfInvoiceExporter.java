package com.invoiceocr.export;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.i18n.MessageKeys;
import com.invoiceocr.i18n.MessageSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes a PDF without any third-party library.
 *
 * <p>The output is a small, conforming PDF 1.4 file: a catalog, a page tree,
 * one content stream per page and the three standard Type 1 fonts, which every
 * reader has built in. That keeps the application's single jar dependency-free;
 * a PDF toolkit would add several megabytes to do rather more than this report
 * needs.</p>
 *
 * <p>Text is encoded as WinAnsi, the encoding those built-in fonts use.
 * Romanian letters outside it are transliterated ({@code ă} becomes {@code a},
 * {@code ș} becomes {@code s}), and anything else unsupported becomes a
 * question mark, so a PDF is always produced rather than an error.</p>
 */
public final class PdfInvoiceExporter implements InvoiceExporter {

    // A4 in PostScript points.
    private static final float PAGE_WIDTH = 595f;
    private static final float PAGE_HEIGHT = 842f;
    private static final float MARGIN = 56f;
    private static final float BOTTOM = 64f;

    private static final String FONT_BOLD = "F1";
    private static final String FONT_REGULAR = "F2";
    private static final String FONT_MONO = "F3";

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private final MessageSource messages;

    public PdfInvoiceExporter(MessageSource messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public ExportFormat format() {
        return ExportFormats.PDF;
    }

    @Override
    public void write(InvoiceData data, OutputStream out) throws IOException {
        Objects.requireNonNull(data, "data");
        List<String> pages = renderPages(layout(data));
        out.write(assemble(pages));
    }

    //-------------------------------------------------------------- layout

    /** One line of text to place on a page. */
    private record Line(String font, float size, float indent, String text, float leading) { }

    private List<Line> layout(InvoiceData data) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(FONT_BOLD, 18f, 0f, messages.get(MessageKeys.REPORT_HEADER), 30f));

        String missing = messages.get(MessageKeys.REPORT_MISSING_VALUE);
        for (ExtractedField field : data.fields()) {
            String label = messages.get(field.definition().labelKey());
            lines.add(new Line(FONT_BOLD, 11f, 0f, label, 0f));
            lines.add(new Line(FONT_REGULAR, 11f, 170f, field.valueOr(missing), 20f));
        }

        lines.add(new Line(FONT_REGULAR, 10f, 0f,
                messages.get(MessageKeys.REPORT_FOOTER, data.recognizedCount(), data.fields().size()), 26f));

        if (!data.source().isBlank()) {
            lines.add(new Line(FONT_BOLD, 12f, 0f, messages.get(MessageKeys.REPORT_RAW_TEXT), 18f));
            for (String raw : data.source().value().split("\r?\n", -1)) {
                for (String wrapped : wrap(raw, 92)) {
                    lines.add(new Line(FONT_MONO, 8.5f, 0f, wrapped, 11f));
                }
            }
        }
        return lines;
    }

    /** Hard-wraps a line at a column count; the mono font makes this exact. */
    private static List<String> wrap(String text, int columns) {
        List<String> parts = new ArrayList<>();
        if (text.isEmpty()) {
            parts.add("");
            return parts;
        }
        for (int start = 0; start < text.length(); start += columns) {
            parts.add(text.substring(start, Math.min(text.length(), start + columns)));
        }
        return parts;
    }

    /** Flows the lines into page content streams. */
    private List<String> renderPages(List<Line> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        float y = PAGE_HEIGHT - MARGIN;

        for (Line line : lines) {
            if (y < BOTTOM) {
                pages.add(page.toString());
                page = new StringBuilder();
                y = PAGE_HEIGHT - MARGIN;
            }
            page.append(show(line.font(), line.size(), MARGIN + line.indent(), y, line.text()));
            if (line.leading() > 0f) {
                y -= line.leading();
            } else {
                // A label and its value share a row.
                y -= 0f;
            }
        }
        pages.add(page.toString());

        List<String> numbered = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            String footer = show(FONT_REGULAR, 8f, MARGIN, BOTTOM - 24f,
                    messages.get(MessageKeys.REPORT_PAGE, i + 1, pages.size()));
            numbered.add(pages.get(i) + footer);
        }
        return numbered;
    }

    private static String show(String font, float size, float x, float y, String text) {
        return "BT /" + font + " " + trim(size) + " Tf 1 0 0 1 " + trim(x) + " " + trim(y) + " Tm ("
                + escape(text) + ") Tj ET\n";
    }

    private static String trim(float value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.valueOf(value);
    }

    //------------------------------------------------------------ assembly

    private byte[] assemble(List<String> pages) throws IOException {
        int pageCount = pages.size();
        int firstPageObject = 6;

        List<String> objects = new ArrayList<>();

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(firstPageObject + i * 2).append(" 0 R ");
        }

        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>");
        objects.add(font("Helvetica-Bold"));
        objects.add(font("Helvetica"));
        objects.add(font("Courier"));

        for (int i = 0; i < pageCount; i++) {
            int contentObject = firstPageObject + i * 2 + 1;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + trim(PAGE_WIDTH) + " " + trim(PAGE_HEIGHT)
                    + "] /Resources << /Font << /" + FONT_BOLD + " 3 0 R /" + FONT_REGULAR + " 4 0 R /"
                    + FONT_MONO + " 5 0 R >> >> /Contents " + contentObject + " 0 R >>");
            objects.add(null);   // placeholder: the stream is written separately
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        out.write(ascii("%PDF-1.4\n"));
        out.write(new byte[] { '%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n' });

        int pageIndex = 0;
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            int number = i + 1;
            String body = objects.get(i);
            if (body != null) {
                out.write(ascii(number + " 0 obj\n" + body + "\nendobj\n"));
            } else {
                byte[] stream = encode(pages.get(pageIndex++));
                out.write(ascii(number + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n"));
                out.write(stream);
                out.write(ascii("\nendstream\nendobj\n"));
            }
        }

        int xrefOffset = out.size();
        StringBuilder xref = new StringBuilder("xref\n0 ").append(objects.size() + 1).append('\n');
        xref.append("0000000000 65535 f \n");
        for (int offset : offsets) {
            xref.append(String.format("%010d 00000 n \n", offset));
        }
        out.write(ascii(xref.toString()));
        out.write(ascii("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n"));

        return out.toByteArray();
    }

    private static String font(String baseFont) {
        return "<< /Type /Font /Subtype /Type1 /BaseFont /" + baseFont + " /Encoding /WinAnsiEncoding >>";
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** Encodes content-stream text as WinAnsi, which is what the fonts declare. */
    private static byte[] encode(String text) throws IOException {
        CharsetEncoder encoder = WIN_ANSI.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            java.nio.ByteBuffer buffer = encoder.encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IOException("Could not encode the PDF content stream", e);
        }
    }

    //------------------------------------------------------------ text prep

    /** Escapes the three characters that are special inside a PDF string. */
    private static String escape(String text) {
        String plain = transliterate(text);
        StringBuilder escaped = new StringBuilder(plain.length() + 8);
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == '\\' || c == '(' || c == ')') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /** Replaces letters the built-in fonts cannot show with their ASCII base. */
    private static String transliterate(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case 'ă', 'Ă' -> out.append(c == 'ă' ? 'a' : 'A');   // a-breve
                case 'ș', 'ş' -> out.append('s');                          // s-comma, s-cedilla
                case 'Ș', 'Ş' -> out.append('S');
                case 'ț', 'ţ' -> out.append('t');                          // t-comma, t-cedilla
                case 'Ț', 'Ţ' -> out.append('T');
                case '–', '—' -> out.append('-');                          // dashes
                case '‘', '’' -> out.append('\'');
                case '“', '”' -> out.append('"');
                case '\t' -> out.append("    ");
                default -> {
                    if (c == '\r' || c == '\n') {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
