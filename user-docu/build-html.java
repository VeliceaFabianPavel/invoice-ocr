///usr/bin/env java --source 21 "$0" "$@"; exit $?

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds the browsable HTML wiki in {@code html/} from the Markdown pages in
 * this folder.
 *
 * <p>The Markdown files stay the single source of truth: edit those, run this,
 * and the site is regenerated. No dependencies, no build tool, no internet
 * access needed.</p>
 *
 * <pre>
 *   cd user-docu
 *   java build-html.java
 * </pre>
 *
 * <p>It understands the Markdown subset actually used by these pages: headings,
 * paragraphs, ordered and unordered lists (including checklists and wrapped
 * items), tables, fenced code, block quotes, rules, links, images, inline code,
 * bold and italic.</p>
 */
public final class BuildHtml {

    /** Sidebar order. Pages not listed here are appended alphabetically. */
    private static final List<String> PAGE_ORDER = List.of(
            "Home.md",
            "Installation.md",
            "Getting-Started.md",
            "The-Main-Window.md",
            "Extracted-Fields.md",
            "Exporting.md",
            "Preparing-Invoices.md",
            "Settings.md",
            "Troubleshooting.md",
            "FAQ.md",
            "Glossary.md");

    private static final String SITE_NAME = "Invoice OCR";
    private static final String SITE_TAGLINE = "User handbook";
    private static final String OUTPUT_DIR = "html";
    private static final String NUL = Character.toString((char) 0);

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path target = source.resolve(OUTPUT_DIR);
        Files.createDirectories(target.resolve("assets"));

        List<Page> pages = loadPages(source);
        if (pages.isEmpty()) {
            System.err.println("No Markdown pages found in " + source);
            return;
        }

        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            Page previous = i > 0 ? pages.get(i - 1) : null;
            Page next = i + 1 < pages.size() ? pages.get(i + 1) : null;
            Files.writeString(target.resolve(page.outputName), render(page, pages, previous, next),
                    StandardCharsets.UTF_8);
            System.out.println("wrote " + OUTPUT_DIR + "/" + page.outputName);
        }

        Files.writeString(target.resolve("assets/wiki.css"), STYLESHEET, StandardCharsets.UTF_8);
        Files.writeString(target.resolve("assets/wiki-index.js"), searchIndex(pages), StandardCharsets.UTF_8);
        Files.writeString(target.resolve("assets/wiki.js"), SCRIPT, StandardCharsets.UTF_8);
        copyImages(source, target);

        System.out.println("wrote " + OUTPUT_DIR + "/assets/ (stylesheet, search index, script)");
        System.out.println("done: " + pages.size() + " pages -> " + target);
    }

    // ---------------------------------------------------------------- pages

    /** One documentation page: its source, its rendered body and its headings. */
    private static final class Page {
        String sourceName;
        String outputName;
        String title;
        String body;
        final List<Heading> headings = new ArrayList<>();
    }

    private record Heading(int level, String id, String text) { }

    private static List<Page> loadPages(Path source) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(source)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> files.put(p.getFileName().toString(), p));
        }

        List<String> ordered = new ArrayList<>(PAGE_ORDER.stream().filter(files::containsKey).toList());
        files.keySet().stream().filter(name -> !ordered.contains(name)).forEach(ordered::add);

        List<Page> pages = new ArrayList<>();
        for (String name : ordered) {
            pages.add(parse(name, Files.readString(files.get(name), StandardCharsets.UTF_8)));
        }
        return pages;
    }

    private static Page parse(String name, String markdown) {
        Page page = new Page();
        page.sourceName = name;
        page.outputName = outputNameFor(name);
        page.body = new Renderer(page).render(stripBreadcrumb(markdown));
        page.title = page.headings.stream()
                .filter(h -> h.level() == 1)
                .map(Heading::text)
                .findFirst()
                .orElse(name.replace(".md", "").replace('-', ' '));
        // The front page's own heading is long by design; the sidebar wants a label.
        if (name.equals("Home.md")) {
            page.title = "Home";
        }
        return page;
    }

    private static String outputNameFor(String markdownName) {
        return markdownName.equals("Home.md") ? "index.html" : markdownName.replace(".md", ".html");
    }

    /** Removes the "Home &rsaquo; Page" line: the sidebar already says where you are. */
    private static String stripBreadcrumb(String markdown) {
        String[] lines = markdown.split("\n", -1);
        if (lines.length > 0 && lines[0].startsWith("[Home](Home.md)")) {
            return String.join("\n", List.of(lines).subList(1, lines.length));
        }
        return markdown;
    }

    // ------------------------------------------------------------ rendering

    /** Line-driven Markdown reader for the subset these pages use. */
    private static final class Renderer {

        private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
        private static final Pattern UNORDERED = Pattern.compile("^[-*]\\s+(.*)$");
        private static final Pattern ORDERED = Pattern.compile("^\\d+\\.\\s+(.*)$");
        private static final Pattern TASK = Pattern.compile("^\\[[ xX]\\]\\s+(.*)$");
        private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\|[\\s:|-]+\\|$");

        private final Page page;
        private final StringBuilder out = new StringBuilder();
        private String[] lines;
        private int at;

        Renderer(Page page) {
            this.page = page;
        }

        String render(String markdown) {
            lines = markdown.split("\n", -1);
            at = 0;
            while (at < lines.length) {
                String line = lines[at];
                if (line.isBlank()) {
                    at++;
                } else if (line.startsWith("```")) {
                    codeBlock();
                } else if (line.strip().equals("---")) {
                    out.append("<hr>\n");
                    at++;
                } else if (HEADING.matcher(line).matches()) {
                    heading();
                } else if (line.startsWith("> ")) {
                    blockQuote();
                } else if (isTableStart()) {
                    table();
                } else if (UNORDERED.matcher(line).matches() || ORDERED.matcher(line).matches()) {
                    list();
                } else {
                    paragraph();
                }
            }
            return out.toString();
        }

        private void heading() {
            Matcher m = HEADING.matcher(lines[at++]);
            m.matches();
            int level = m.group(1).length();
            String text = m.group(2).strip();
            String id = slug(text);
            page.headings.add(new Heading(level, id, stripInline(text)));
            out.append("<h").append(level).append(" id=\"").append(id).append("\">")
                    .append(inline(text))
                    .append("</h").append(level).append(">\n");
        }

        private void codeBlock() {
            at++;
            StringBuilder code = new StringBuilder();
            while (at < lines.length && !lines[at].startsWith("```")) {
                code.append(escape(lines[at++])).append('\n');
            }
            at++;
            out.append("<pre><code>").append(code.toString().stripTrailing()).append("</code></pre>\n");
        }

        private void blockQuote() {
            StringBuilder quoted = new StringBuilder();
            while (at < lines.length && (lines[at].startsWith(">"))) {
                quoted.append(lines[at++].replaceFirst("^>\\s?", "")).append('\n');
            }
            out.append("<blockquote>\n").append(new Renderer(page).render(quoted.toString())).append("</blockquote>\n");
        }

        private boolean isTableStart() {
            return lines[at].startsWith("|")
                    && at + 1 < lines.length
                    && TABLE_DIVIDER.matcher(lines[at + 1].strip()).matches();
        }

        private void table() {
            List<String> header = splitRow(lines[at]);
            at += 2;
            out.append("<div class=\"table-wrap\">\n<table>\n<thead>\n<tr>");
            header.forEach(cell -> out.append("<th>").append(inline(cell)).append("</th>"));
            out.append("</tr>\n</thead>\n<tbody>\n");
            while (at < lines.length && lines[at].startsWith("|")) {
                out.append("<tr>");
                splitRow(lines[at++]).forEach(cell -> out.append("<td>").append(inline(cell)).append("</td>"));
                out.append("</tr>\n");
            }
            out.append("</tbody>\n</table>\n</div>\n");
        }

        /** Splits on pipes that are neither escaped nor inside a code span. */
        private static List<String> splitRow(String line) {
            String row = line.strip();
            if (row.startsWith("|")) {
                row = row.substring(1);
            }
            if (row.endsWith("|") && !row.endsWith("\\|")) {
                row = row.substring(0, row.length() - 1);
            }
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean inCode = false;
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == '`') {
                    inCode = !inCode;
                    cell.append(c);
                } else if (c == '\\' && i + 1 < row.length() && row.charAt(i + 1) == '|') {
                    cell.append('|');
                    i++;
                } else if (c == '|' && !inCode) {
                    cells.add(cell.toString().strip());
                    cell.setLength(0);
                } else {
                    cell.append(c);
                }
            }
            cells.add(cell.toString().strip());
            return cells;
        }

        private void list() {
            boolean ordered = ORDERED.matcher(lines[at]).matches();
            List<String> items = new ArrayList<>();
            boolean checklist = false;

            while (at < lines.length) {
                String line = lines[at];
                Matcher m = ordered ? ORDERED.matcher(line) : UNORDERED.matcher(line);
                if (m.matches()) {
                    items.add(m.group(1).strip());
                    at++;
                } else if (!line.isBlank() && Character.isWhitespace(line.charAt(0)) && !items.isEmpty()) {
                    // A wrapped item: fold the continuation into the previous entry.
                    items.set(items.size() - 1, items.get(items.size() - 1) + " " + line.strip());
                    at++;
                } else {
                    break;
                }
            }

            List<String> rendered = new ArrayList<>();
            for (String item : items) {
                Matcher task = TASK.matcher(item);
                if (task.matches()) {
                    checklist = true;
                    rendered.add("<li>" + inline(task.group(1)) + "</li>");
                } else {
                    rendered.add("<li>" + inline(item) + "</li>");
                }
            }

            String tag = ordered ? "ol" : "ul";
            out.append('<').append(tag);
            if (checklist) {
                out.append(" class=\"check\"");
            }
            out.append(">\n");
            rendered.forEach(li -> out.append(li).append('\n'));
            out.append("</").append(tag).append(">\n");
        }

        private void paragraph() {
            StringBuilder text = new StringBuilder();
            while (at < lines.length && !lines[at].isBlank()
                    && !lines[at].startsWith("```")
                    && !lines[at].startsWith("|")
                    && !lines[at].startsWith("> ")
                    && !lines[at].strip().equals("---")
                    && !HEADING.matcher(lines[at]).matches()
                    && !UNORDERED.matcher(lines[at]).matches()
                    && !ORDERED.matcher(lines[at]).matches()) {
                text.append(text.isEmpty() ? "" : " ").append(lines[at++].strip());
            }
            String content = text.toString();
            if (content.isBlank()) {
                return;
            }
            String rendered = inline(content);
            String cssClass = rendered.startsWith("<img") ? " class=\"figure\"" : "";
            out.append("<p").append(cssClass).append('>').append(rendered).append("</p>\n");
        }
    }

    // -------------------------------------------------------------- inline

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*([^*]+)\\*");

    /**
     * Renders inline markup. Code spans are lifted out first so that their
     * contents are never mistaken for emphasis or a link.
     */
    private static String inline(String markdown) {
        String text = escape(markdown);

        List<String> spans = new ArrayList<>();
        Matcher code = CODE_SPAN.matcher(text);
        StringBuilder withPlaceholders = new StringBuilder();
        while (code.find()) {
            spans.add(code.group(1));
            code.appendReplacement(withPlaceholders, Matcher.quoteReplacement(NUL + (spans.size() - 1) + NUL));
        }
        code.appendTail(withPlaceholders);
        text = withPlaceholders.toString();

        text = IMAGE.matcher(text).replaceAll(m ->
                "<img src=\"" + m.group(2) + "\" alt=\"" + m.group(1) + "\" loading=\"lazy\">");
        text = LINK.matcher(text).replaceAll(m ->
                "<a href=\"" + mapLink(m.group(2)) + "\">" + m.group(1) + "</a>");
        text = BOLD.matcher(text).replaceAll(m -> "<strong>" + m.group(1) + "</strong>");
        text = ITALIC.matcher(text).replaceAll(m -> "<em>" + m.group(1) + "</em>");

        for (int i = 0; i < spans.size(); i++) {
            text = text.replace(NUL + i + NUL, "<code>" + spans.get(i) + "</code>");
        }
        return text;
    }

    /** Plain text of an inline string, for titles and the search index. */
    private static String stripInline(String markdown) {
        String text = markdown.replaceAll("`([^`]+)`", "$1");
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        return text.strip();
    }

    /** Rewrites cross-page links from Markdown to their generated HTML names. */
    private static String mapLink(String target) {
        if (target.startsWith("http") || target.startsWith("#")) {
            return target;
        }
        int hash = target.indexOf('#');
        String path = hash < 0 ? target : target.substring(0, hash);
        String anchor = hash < 0 ? "" : target.substring(hash);
        if (path.endsWith(".md")) {
            path = outputNameFor(path);
        }
        return path + anchor;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** GitHub-compatible heading slug, so anchors written in Markdown keep working. */
    private static String slug(String heading) {
        String s = stripInline(heading).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^\\p{L}\\p{N}\\s-]", "");
        return s.strip().replaceAll("\\s+", "-");
    }

    // --------------------------------------------------------------- output

    private static String render(Page page, List<Page> pages, Page previous, Page next) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(escape(page.title)).append(" &middot; ").append(SITE_NAME)
                .append(" Handbook</title>\n")
                .append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n")
                .append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n")
                .append("<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?")
                .append("family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@400;500;600")
                .append("&family=Zilla+Slab:wght@500;600;700&display=swap\">\n")
                .append("<link rel=\"stylesheet\" href=\"assets/wiki.css\">\n")
                .append("</head>\n<body>\n");

        html.append("<a class=\"skip\" href=\"#content\">Skip to content</a>\n");
        html.append("<div class=\"shell\">\n");

        // Sidebar
        html.append("<nav class=\"sidebar\" aria-label=\"Handbook\">\n")
                .append("<a class=\"brand\" href=\"index.html\">")
                .append("<span class=\"brand-name\">").append(SITE_NAME).append("</span>")
                .append("<span class=\"brand-tag\">").append(SITE_TAGLINE).append("</span></a>\n")
                .append("<div class=\"search\">\n")
                .append("<input type=\"search\" id=\"q\" placeholder=\"Search the handbook\" ")
                .append("autocomplete=\"off\" aria-label=\"Search the handbook\">\n")
                .append("<ul id=\"results\" hidden></ul>\n</div>\n")
                .append("<ol class=\"pages\">\n");
        for (Page other : pages) {
            boolean current = other == page;
            html.append("<li><a href=\"").append(other.outputName).append('"');
            if (current) {
                html.append(" aria-current=\"page\"");
            }
            html.append('>').append(escape(other.title)).append("</a>");
            if (current) {
                List<Heading> sections = other.headings.stream().filter(h -> h.level() == 2).toList();
                if (!sections.isEmpty()) {
                    html.append("\n<ul class=\"sections\">\n");
                    for (Heading heading : sections) {
                        html.append("<li><a href=\"#").append(heading.id()).append("\">")
                                .append(escape(heading.text())).append("</a></li>\n");
                    }
                    html.append("</ul>\n");
                }
            }
            html.append("</li>\n");
        }
        html.append("</ol>\n</nav>\n");

        // Article
        html.append("<main id=\"content\">\n<article>\n").append(page.body).append("</article>\n");

        html.append("<nav class=\"pager\" aria-label=\"Pagination\">\n");
        if (previous != null) {
            html.append("<a class=\"prev\" href=\"").append(previous.outputName).append("\">")
                    .append("<span>Previous</span>").append(escape(previous.title)).append("</a>\n");
        } else {
            html.append("<span></span>\n");
        }
        if (next != null) {
            html.append("<a class=\"next\" href=\"").append(next.outputName).append("\">")
                    .append("<span>Next</span>").append(escape(next.title)).append("</a>\n");
        }
        html.append("</nav>\n</main>\n</div>\n");

        html.append("<script src=\"assets/wiki-index.js\"></script>\n")
                .append("<script src=\"assets/wiki.js\"></script>\n")
                .append("</body>\n</html>\n");
        return html.toString();
    }

    private static String searchIndex(List<Page> pages) {
        StringBuilder js = new StringBuilder("// Generated by build-html.java\nconst WIKI_INDEX = [\n");
        for (Page page : pages) {
            js.append("  { page: ").append(quote(page.outputName))
                    .append(", title: ").append(quote(page.title)).append(", headings: [");
            List<Heading> searchable = page.headings.stream().filter(h -> h.level() >= 2).toList();
            for (int i = 0; i < searchable.size(); i++) {
                Heading heading = searchable.get(i);
                js.append(i == 0 ? "" : ", ")
                        .append("{ id: ").append(quote(heading.id()))
                        .append(", text: ").append(quote(heading.text())).append(" }");
            }
            js.append("] },\n");
        }
        return js.append("];\n").toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void copyImages(Path source, Path target) throws IOException {
        Path images = source.resolve("images");
        if (!Files.isDirectory(images)) {
            return;
        }
        Path destination = target.resolve("images");
        Files.createDirectories(destination);
        try (Stream<Path> stream = Files.list(images)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private BuildHtml() {
    }

    // ------------------------------------------------------------- assets

    private static final String STYLESHEET = """
            /* Invoice OCR handbook. Generated by build-html.java - edit the
               STYLESHEET constant there, not this file. */

            :root {
              --paper:      #F7F8F5;
              --card:       #FFFFFF;
              --bar:        #ECF1E9;
              --ink:        #16211C;
              --ink-soft:   #55645C;
              --ink-faint:  #7C8A82;
              --rule:       #D6DED7;
              --rule-firm:  #B6C2B8;
              --accent:     #1D6A4F;
              --accent-ink: #12513C;
              --accent-bg:  #E3EFE7;
              --alert:      #9A3324;
              --shadow:     0 1px 2px rgba(22,33,28,.06), 0 8px 24px rgba(22,33,28,.05);
              --measure: 68ch;
            }

            @media (prefers-color-scheme: dark) {
              :root:not([data-theme="light"]) {
                --paper:      #101410;
                --card:       #161B16;
                --bar:        #1C231D;
                --ink:        #E5EBE3;
                --ink-soft:   #A2AEA5;
                --ink-faint:  #7E8B81;
                --rule:       #2A332B;
                --rule-firm:  #3D4A3F;
                --accent:     #63C495;
                --accent-ink: #8AD8B0;
                --accent-bg:  #16281F;
                --alert:      #E38C78;
                --shadow:     0 1px 2px rgba(0,0,0,.4), 0 8px 24px rgba(0,0,0,.3);
              }
            }

            :root[data-theme="dark"] {
              --paper:      #101410;
              --card:       #161B16;
              --bar:        #1C231D;
              --ink:        #E5EBE3;
              --ink-soft:   #A2AEA5;
              --ink-faint:  #7E8B81;
              --rule:       #2A332B;
              --rule-firm:  #3D4A3F;
              --accent:     #63C495;
              --accent-ink: #8AD8B0;
              --accent-bg:  #16281F;
              --alert:      #E38C78;
              --shadow:     0 1px 2px rgba(0,0,0,.4), 0 8px 24px rgba(0,0,0,.3);
            }

            * { box-sizing: border-box; }

            body {
              margin: 0;
              background: var(--paper);
              color: var(--ink);
              font-family: "IBM Plex Sans", ui-sans-serif, system-ui, "Segoe UI", sans-serif;
              font-size: 1rem;
              line-height: 1.65;
              -webkit-font-smoothing: antialiased;
            }

            h1, h2, h3, h4 {
              font-family: "Zilla Slab", Rockwell, Georgia, serif;
              line-height: 1.18;
              text-wrap: balance;
              margin: 0;
            }

            a { color: var(--accent); text-underline-offset: .18em; }
            a:hover { color: var(--accent-ink); }
            :focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; border-radius: 2px; }

            code, kbd, pre { font-family: "IBM Plex Mono", ui-monospace, Consolas, monospace; }
            code {
              font-size: .88em;
              background: var(--bar);
              border: 1px solid var(--rule);
              border-radius: 3px;
              padding: .08em .34em;
            }
            pre {
              margin: 1.1rem 0 1.5rem;
              padding: 1rem 1.1rem;
              background: var(--card);
              border: 1px solid var(--rule);
              border-left: 3px solid var(--rule-firm);
              border-radius: 4px;
              overflow-x: auto;
              font-size: .86rem;
              line-height: 1.6;
              max-width: var(--measure);
              /* Long error messages must stay readable rather than hide in a scroll. */
              white-space: pre-wrap;
              overflow-wrap: break-word;
            }
            pre code { background: none; border: 0; padding: 0; font-size: inherit; }

            .skip {
              position: absolute;
              left: -9999px;
              background: var(--card);
              padding: .6rem 1rem;
              border: 1px solid var(--accent);
              border-radius: 4px;
            }
            .skip:focus { left: 1rem; top: 1rem; z-index: 10; }

            .shell {
              max-width: 1280px;
              margin: 0 auto;
              display: grid;
              grid-template-columns: 17.5rem minmax(0, 1fr);
              gap: clamp(1.5rem, 4vw, 4rem);
              align-items: start;
              padding: 0 clamp(1rem, 3vw, 2.5rem);
            }

            /* ---- Sidebar ---- */
            .sidebar {
              position: sticky;
              top: 0;
              max-height: 100vh;
              overflow-y: auto;
              padding: 2rem 0 3rem;
              border-right: 1px solid var(--rule);
              padding-right: 1.5rem;
            }
            .brand { display: block; text-decoration: none; color: var(--ink); margin-bottom: 1.2rem; }
            .brand-name {
              display: block;
              font-family: "Zilla Slab", Georgia, serif;
              font-size: 1.5rem;
              font-weight: 700;
              letter-spacing: -.01em;
            }
            .brand-tag {
              display: block;
              font-family: "IBM Plex Mono", monospace;
              font-size: .72rem;
              letter-spacing: .14em;
              text-transform: uppercase;
              color: var(--ink-faint);
            }

            .search { position: relative; margin-bottom: 1.4rem; }
            .search input {
              width: 100%;
              font: inherit;
              font-size: .9rem;
              padding: .5rem .7rem;
              color: var(--ink);
              background: var(--card);
              border: 1px solid var(--rule-firm);
              border-radius: 4px;
            }
            .search input::placeholder { color: var(--ink-faint); }
            #results {
              position: absolute;
              z-index: 5;
              left: 0;
              right: 0;
              margin: .3rem 0 0;
              padding: .3rem;
              list-style: none;
              max-height: 22rem;
              overflow-y: auto;
              background: var(--card);
              border: 1px solid var(--rule-firm);
              border-radius: 4px;
              box-shadow: var(--shadow);
            }
            #results li { margin: 0; }
            #results a {
              display: block;
              padding: .35rem .5rem;
              border-radius: 3px;
              text-decoration: none;
              color: var(--ink);
              font-size: .88rem;
            }
            #results a:hover, #results a:focus { background: var(--accent-bg); color: var(--accent-ink); }
            #results .in { display: block; font-size: .74rem; color: var(--ink-faint); }
            #results .empty { padding: .5rem; color: var(--ink-faint); font-size: .85rem; }

            .pages { list-style: none; margin: 0; padding: 0; counter-reset: page; }
            .pages > li { margin: 0; }
            .pages > li > a {
              display: block;
              padding: .34rem 0 .34rem .8rem;
              border-left: 2px solid var(--rule);
              color: var(--ink-soft);
              text-decoration: none;
              font-size: .95rem;
            }
            .pages > li > a:hover { color: var(--ink); border-left-color: var(--rule-firm); }
            .pages > li > a[aria-current="page"] {
              color: var(--accent-ink);
              border-left-color: var(--accent);
              font-weight: 600;
            }
            .sections { list-style: none; margin: .1rem 0 .5rem; padding: 0; }
            .sections a {
              display: block;
              padding: .2rem 0 .2rem 1.6rem;
              border-left: 2px solid var(--rule);
              color: var(--ink-faint);
              text-decoration: none;
              font-size: .85rem;
            }
            .sections a:hover { color: var(--accent-ink); border-left-color: var(--rule-firm); }

            /* ---- Article ---- */
            main { padding: 2.4rem 0 4rem; min-width: 0; }
            article h1 {
              font-size: clamp(2rem, 1.6rem + 1.8vw, 2.9rem);
              font-weight: 700;
              letter-spacing: -.015em;
              margin-bottom: .8rem;
            }
            article h2 {
              font-size: clamp(1.4rem, 1.25rem + .7vw, 1.8rem);
              font-weight: 600;
              margin: 2.8rem 0 .6rem;
              padding-top: 1.4rem;
              border-top: 1px solid var(--rule);
            }
            article h1 + h2 { border-top: 0; padding-top: 0; margin-top: 1.6rem; }
            article h3 { font-size: 1.25rem; font-weight: 600; margin: 2rem 0 .5rem; }
            article h4 {
              font-family: "IBM Plex Sans", sans-serif;
              font-size: 1rem;
              font-weight: 600;
              margin: 1.5rem 0 .3rem;
            }
            article p, article ul, article ol { max-width: var(--measure); }
            article p { margin: 0 0 1rem; }
            article ul, article ol { margin: 0 0 1.1rem; padding-left: 1.35rem; }
            article li { margin-bottom: .35rem; }
            article li::marker { color: var(--ink-faint); }
            hr { border: 0; border-top: 1px solid var(--rule); margin: 2.4rem 0 0; }
            /* A rule before a heading is already the section break: don't draw a second one. */
            hr + h2 { border-top: 0; padding-top: 0; margin-top: 1.8rem; }

            blockquote {
              margin: 1.4rem 0;
              padding: .9rem 1.1rem;
              background: var(--accent-bg);
              border: 1px solid var(--rule);
              border-left: 3px solid var(--accent);
              border-radius: 4px;
              max-width: var(--measure);
            }
            blockquote p:last-child { margin-bottom: 0; }
            blockquote strong { color: var(--accent-ink); }

            p.figure { max-width: none; margin: 1.6rem 0 2rem; }
            p.figure img {
              display: block;
              width: 100%;
              max-width: 62rem;
              height: auto;
              border: 1px solid var(--rule-firm);
              border-radius: 4px;
              box-shadow: var(--shadow);
              background: var(--card);
            }

            ul.check { list-style: none; padding-left: 0; }
            ul.check li { position: relative; padding-left: 1.7rem; }
            ul.check li::before {
              content: "";
              position: absolute;
              left: 0;
              top: .45em;
              width: .85rem;
              height: .85rem;
              border: 1px solid var(--rule-firm);
              border-radius: 2px;
              background: var(--card);
            }

            /* ---- Green-bar tables, after continuous accounting stationery ---- */
            .table-wrap {
              overflow-x: auto;
              margin: 1.2rem 0 1.7rem;
              border: 1px solid var(--rule);
              border-radius: 4px;
              background: var(--card);
            }
            table { border-collapse: collapse; width: 100%; font-size: .93rem; }
            thead th {
              text-align: left;
              font-family: "IBM Plex Mono", monospace;
              font-weight: 500;
              font-size: .72rem;
              letter-spacing: .1em;
              text-transform: uppercase;
              color: var(--ink-soft);
              padding: .7rem .9rem;
              border-bottom: 1px solid var(--rule-firm);
              white-space: nowrap;
            }
            tbody td { padding: .62rem .9rem; vertical-align: top; border-bottom: 1px solid var(--rule); }
            tbody tr:nth-child(even) { background: var(--bar); }
            tbody tr:last-child td { border-bottom: 0; }
            td code { white-space: nowrap; }

            /* ---- Pager ---- */
            .pager {
              display: flex;
              justify-content: space-between;
              gap: 1rem;
              margin-top: 3rem;
              padding-top: 1.6rem;
              border-top: 3px double var(--rule-firm);
            }
            .pager a {
              flex: 0 1 auto;
              max-width: 45%;
              text-decoration: none;
              color: var(--ink);
              font-family: "Zilla Slab", Georgia, serif;
              font-size: 1.05rem;
              font-weight: 600;
              padding: .7rem 1rem;
              border: 1px solid var(--rule);
              border-radius: 4px;
              background: var(--card);
            }
            .pager a:hover { border-color: var(--accent); color: var(--accent-ink); }
            .pager a.next { text-align: right; margin-left: auto; }
            .pager span {
              display: block;
              font-family: "IBM Plex Mono", monospace;
              font-size: .7rem;
              letter-spacing: .12em;
              text-transform: uppercase;
              color: var(--ink-faint);
              font-weight: 400;
            }

            @media (max-width: 900px) {
              .shell { grid-template-columns: minmax(0, 1fr); }
              .sidebar {
                position: static;
                max-height: none;
                border-right: 0;
                border-bottom: 1px solid var(--rule);
                padding: 1.5rem 0 1rem;
              }
              .pages { display: flex; flex-wrap: wrap; gap: .2rem .4rem; }
              .pages > li > a { border-left: 0; border-bottom: 2px solid var(--rule); padding: .2rem .5rem; }
              .pages > li > a[aria-current="page"] { border-left: 0; border-bottom-color: var(--accent); }
              .sections { display: none; }
              main { padding-top: 1.6rem; }
            }

            @media (prefers-reduced-motion: no-preference) {
              html { scroll-behavior: smooth; }
            }

            @media print {
              .sidebar, .pager, .skip { display: none; }
              .shell { display: block; }
              body { background: #fff; color: #000; }
            }
            """;

    private static final String SCRIPT = """
            // Handbook search. Generated by build-html.java.
            (function () {
              var box = document.getElementById('q');
              var list = document.getElementById('results');
              if (!box || !list || typeof WIKI_INDEX === 'undefined') { return; }

              function hide() { list.hidden = true; list.innerHTML = ''; }

              function show(matches) {
                list.innerHTML = '';
                if (!matches.length) {
                  var empty = document.createElement('li');
                  empty.className = 'empty';
                  empty.textContent = 'Nothing found';
                  list.appendChild(empty);
                  list.hidden = false;
                  return;
                }
                matches.slice(0, 12).forEach(function (match) {
                  var item = document.createElement('li');
                  var link = document.createElement('a');
                  link.href = match.href;
                  link.textContent = match.label;
                  if (match.context) {
                    var context = document.createElement('span');
                    context.className = 'in';
                    context.textContent = match.context;
                    link.appendChild(context);
                  }
                  item.appendChild(link);
                  list.appendChild(item);
                });
                list.hidden = false;
              }

              function search(term) {
                var needle = term.trim().toLowerCase();
                if (needle.length < 2) { hide(); return; }
                var matches = [];
                WIKI_INDEX.forEach(function (page) {
                  if (page.title.toLowerCase().indexOf(needle) !== -1) {
                    matches.push({ href: page.page, label: page.title, context: 'Page' });
                  }
                  page.headings.forEach(function (heading) {
                    if (heading.text.toLowerCase().indexOf(needle) !== -1) {
                      matches.push({
                        href: page.page + '#' + heading.id,
                        label: heading.text,
                        context: page.title
                      });
                    }
                  });
                });
                show(matches);
              }

              box.addEventListener('input', function () { search(box.value); });
              box.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') { box.value = ''; hide(); box.blur(); }
              });
              document.addEventListener('click', function (event) {
                if (!event.target.closest('.search')) { hide(); }
              });
              document.addEventListener('keydown', function (event) {
                if (event.key === '/' && document.activeElement !== box) {
                  event.preventDefault();
                  box.focus();
                }
              });
            })();
            """;
}
