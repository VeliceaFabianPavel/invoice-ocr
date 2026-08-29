# Invoice OCR

Desktop application that reads an invoice image, runs OCR over it, extracts the
six fields an accountant would otherwise retype, and exports them as PDF, TXT,
Markdown, HTML, JSON, XML or CSV.

Java 17 rewrite of a single-file C++/CLI program. Same behaviour, but every
responsibility is a separate, substitutable piece.

![The application after processing an invoice](user-docu/images/result.png)

| | |
|---|---|
| **Version** | 1.1.1 |
| **Language** | Java 17 (built and tested on JDK 21) |
| **UI** | Swing, Romanian and English |
| **Runtime dependencies** | one: Tess4J (which bundles the native OCR engine) |
| **Size** | 103 main classes / ~5,100 lines, 27 test classes / ~2,500 lines |
| **Tests** | 154, all green, no native library required |
| **Recognition** | 60 / 60 fields across a 10-layout corpus |
| **Docs** | 11-page user wiki, ~7,600 words |

> **Using the application rather than working on it?**
> Start at the user guide: [user-docu/Home.md](user-docu/Home.md), or open
> [user-docu/html/index.html](user-docu/html/index.html) for the browsable wiki.
> This README is the developer's view.

---

## Contents

- [Quick start](#quick-start)
- [Requirements](#requirements)
- [Configuration](#configuration)
- [How it works](#how-it-works)
- [Architecture](#architecture)
- [Project layout](#project-layout)
- [The domain model](#the-domain-model)
- [Extraction](#extraction)
- [Export](#export)
- [Internationalisation](#internationalisation)
- [Error handling](#error-handling)
- [Concurrency](#concurrency)
- [Testing](#testing)
- [Extending it](#extending-it)
- [The installer](#the-installer)
- [API documentation](#api-documentation)
- [User documentation](#user-documentation)
- [Differences from the C++ original](#differences-from-the-c-original)
- [What has actually been verified](#what-has-actually-been-verified)
- [Known limitations](#known-limitations)
- [Changelog](#changelog)

---

## Quick start

```bash
mvn clean package
java -jar target/invoice-ocr.jar
```

`mvn test` runs the suite. `target/invoice-ocr.jar` is a shaded, self-contained
jar — the only thing it needs from outside is a `tessdata` folder.

Without Maven on your PATH, the whole project compiles with plain `javac`
against the Tess4J jar; see [Testing](#testing) for the exact commands.

### Getting language data

The OCR engine itself ships inside the jar. Its **language data** does not, and
that is the one thing you must supply:

```bash
winget install UB-Mannheim.TesseractOCR      # installs C:\Program Files\Tesseract-OCR\tessdata
```

Or download individual `*.traineddata` files from
[tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) into any folder
and point `ocr.tessdata.path` at it. `eng` is enough to start; add `ron` for
Romanian invoices.

---

## Requirements

| | |
|---|---|
| JDK | 17 or newer (the app also checks this at install time) |
| Maven | 3.8+ (optional — a local `javac` path is documented below) |
| OS | Any that runs Java + Swing. The installer is Windows-only |
| Language data | A `tessdata` folder with at least `eng.traineddata` |

Dependencies, both declared in [pom.xml](pom.xml):

| Artifact | Version | Scope | Why |
|---|---|---|---|
| `net.sourceforge.tess4j:tess4j` | 5.11.0 | compile | JNA binding over libtesseract; ships the native libraries |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 | test | Tests |

Nothing else. PDF export is hand-written rather than pulling in a PDF toolkit —
see [Export](#export).

---

## Configuration

Nine settings, all optional except the tessdata path.

| Setting | Default | Meaning |
|---|---|---|
| `ocr.tessdata.path` | `C:/Program Files/Tesseract-OCR/tessdata` | Folder holding `*.traineddata`. **The one you must get right** |
| `ocr.language` | `eng` | Language(s) to recognise; combine with `+`, e.g. `ron+eng` |
| `ocr.pageSegmentationMode` | `3` | Tesseract PSM. `3` automatic, `4` single column, `6` single block, `11` sparse |
| `ocr.engineMode` | `3` | Tesseract OEM. `3` default, `1` LSTM only |
| `document.supportedExtensions` | `png,jpg,jpeg,bmp,tif,tiff,gif` | Formats offered in the open dialog and accepted by the loader |
| `image.preprocessing.enabled` | `true` | Whether images are prepared before OCR |
| `image.preprocessing.minimumWidth` | `1000` | Images narrower than this are upscaled (max 4×); `0` disables |
| `ui.locale` | `ro` | Interface language: `ro` or `en` |
| `export.defaultFormat` | `pdf` | Format the save dialog opens on |

### Precedence

Each source overrides the ones below it, so a deployment override never needs a
rebuild:

| # | Source | Example |
|---|---|---|
| 1 | JVM system property | `java -Docr.tessdata.path=D:/tessdata -jar invoice-ocr.jar` |
| 2 | Environment variable | `set OCR_TESSDATA_PATH=D:/tessdata` |
| 3 | `invoice-ocr.properties` next to the jar | `ocr.language=ron+eng` |
| 4 | Bundled [application.properties](src/main/resources/application.properties) | the defaults above |

Environment names are the dotted key uppercased with underscores:
`ocr.tessdata.path` → `OCR_TESSDATA_PATH`.

**Use forward slashes in paths.** A `.properties` file reads `\` as an escape
character, so `C:\Program Files\...` is silently mangled. The installers write
forward slashes for exactly this reason.

### How configuration is modelled

```
ConfigurationSource  (Optional<String> find(String key))
  ├── SystemPropertiesConfigurationSource
  ├── EnvironmentConfigurationSource      (dotted key → SCREAMING_SNAKE_CASE)
  ├── PropertiesConfigurationSource       (file or classpath)
  └── ChainedConfigurationSource          (first non-blank wins)
                 │
                 ├──▶ OcrSettings     (typed, validated: paths, ints, lists)
                 └──▶ ExportSettings  (typed: default export format)
```

Consumers depend on the small typed interfaces, never on raw strings, so nothing
downstream parses or validates. A blank value counts as absent, so an empty
override cannot mask a real setting.

---

## How it works

One pass, five stages, each behind an interface:

```
   file on disk
        │
        ▼  DocumentLoader          ImageIoDocumentLoader, extension-checked
   SourceImage (BufferedImage + origin)
        │
        ▼  ImagePreprocessor       Upscale → Grayscale (composed)
   SourceImage
        │
        ▼  OcrEngine               TesseractOcrEngine, created per run
   RecognizedText
        │
        ▼  InvoiceParser           RuleBasedInvoiceParser + rule provider
   InvoiceData  ───▶ InvoiceReportFormatter ───▶ the right-hand panel
                └──▶ InvoiceExportService   ───▶ a file
```

`DefaultInvoiceRecognitionService` owns nothing but the order of these calls; it
holds no format, engine or regex knowledge. The engine is created per run and
closed in a try-with-resources, so a failure mid-recognition still releases the
native handle — there is a test for that.

Recognition of a 1240×900 page takes roughly 0.4–0.5 s on a modern laptop.

---

## Architecture

Dependencies point inwards. The UI knows the service; the service knows
interfaces; nothing knows about Tesseract except one package.

```
ui.swing ──▶ presentation ──▶ service ──▶ ocr ──▶ ocr.tesseract
                  │              │        image
                  ├──▶ export    │        extraction ──▶ extraction.rules
                  ▼              ▼        extraction.normalization
               format         domain
```

| Package | Classes | Responsibility |
|---|--:|---|
| `domain` | 6 | Value types. No behaviour beyond validation |
| `config` | 11 | Untyped sources, typed views over them |
| `i18n` | 3 | `MessageSource`; no user-visible string is hard-coded |
| `image` | 6 | File → image, image → image |
| `ocr` | 2 | `OcrEngine`, `OcrEngineFactory` — the native seam |
| `ocr.tesseract` | 3 | The only package that imports Tess4J |
| `extraction` | 13 | *Where* a value is: the strategies, rules and parser |
| `extraction.text` | 7 | The page prepared for searching: folding, regions, value shapes, digit repair |
| `extraction.normalization` | 11 | *How it should look* once found |
| `extraction.rules` | 1 | Which strategy ladder applies to which document dialect |
| `format` | 8 | Rendering `InvoiceData` as text in six shapes |
| `export` | 7 | Which format goes to which file, written safely |
| `service` | 2 | The use case |
| `concurrent` | 3 | `TaskExecutor`, keeping OCR off the event thread |
| `ui` | 6 | Toolkit-free contracts |
| `ui.swing` | 5 | The Swing implementations |
| `presentation` | 1 | `InvoicePresenter`: intents in, view updates out |
| `exception` | 6 | One typed hierarchy for every failure |
| `app` | 2 | Composition root and `main` |

### Why the seams are where they are

- **`OcrEngine` as an interface.** Tesseract needs a native library and a
  tessdata directory. Behind an interface the whole pipeline is testable with a
  fake engine, and a cloud OCR backend is a one-line change in the assembler.
- **Matching split from normalising.** A regex decides *where* a value is; a
  `ValueNormalizer` decides *how it should look*. `1.234,56`, `1,234.56` and
  `1 234,56` all become `1234.56` without touching a single pattern.
- **Rules as data.** A field is a `FieldDefinition` plus an `ExtractionRule`, not
  a branch in a method. Adding one changes no existing class.
- **`format` separate from `export`.** Rendering produces a string; exporting
  writes bytes to a file. `TextInvoiceExporter` adapts any formatter into an
  exporter, so each new text format costs one small formatter and nothing else.
- **`TaskExecutor`.** The original blocked the UI thread for the whole OCR run.
  Here the presenter hands work to an executor: Swing gets `SwingTaskExecutor`,
  tests get `DirectTaskExecutor` and stay synchronous.
- **Toolkit-free UI contracts.** `InvoiceView`, `DocumentChooser`,
  `ExportChooser` and `UserNotifier` mention no Swing type, so
  `InvoicePresenter` is unit-tested against fakes with no window on screen.
- **Composition root.** [`ApplicationAssembler`](src/main/java/com/invoiceocr/app/ApplicationAssembler.java)
  is the only class that names concrete implementations. Every other class takes
  its collaborators through the constructor. Each wiring step is a `protected`
  method, so a variant assembler overrides one method rather than rewriting the
  graph.

---

## Project layout

```
alin/
├── pom.xml                     Maven build, shade plugin, Java 17 target
├── README.md                   this file
├── src/main/java/com/invoiceocr/
│   ├── app/                    ApplicationAssembler, InvoiceOcrApplication
│   ├── concurrent/             TaskExecutor, Swing + Direct implementations
│   ├── config/                 ConfigurationSource ×4, OcrSettings, ExportSettings, SettingKeys
│   ├── domain/                 FieldDefinition, InvoiceFields, ExtractedField,
│   │                           InvoiceData, RecognizedText, SourceImage
│   ├── exception/              InvoiceOcrException + 5 subtypes
│   ├── export/                 ExportFormat(s), InvoiceExporter, TextInvoiceExporter,
│   │                           PdfInvoiceExporter, DefaultInvoiceExportService
│   ├── extraction/             FieldExtractor, RegexFieldExtractor, FirstMatchFieldExtractor,
│   │   │                       ExtractionRule(+Builder), ExtractionRuleProvider, parser
│   │   ├── normalization/      ValueNormalizer + 7 implementations + Normalizers
│   │   └── rules/              RomanianInvoiceRuleProvider
│   ├── format/                 PlainText, Markdown, Html, Json, Xml, Csv, RawTextAppending
│   ├── i18n/                   MessageSource, ResourceBundleMessageSource, MessageKeys
│   ├── image/                  DocumentLoader, ImagePreprocessor, Grayscale, Upscale, Composite
│   ├── ocr/                    OcrEngine, OcrEngineFactory
│   │   └── tesseract/          TesseractOcrEngine, factory, TesseractInstallation
│   ├── presentation/           InvoicePresenter
│   ├── service/                InvoiceRecognitionService + default
│   └── ui/                     InvoiceView, listeners, choosers, UserNotifier
│       └── swing/              SwingInvoiceView, chooser ×2, notifier, TitledTextPanel
├── src/main/resources/         application.properties, messages.properties, messages_ro.properties
├── src/test/java/…             16 test classes + 6 fakes in support/
├── installer/                  NSIS script, PowerShell installer, build script, icon
└── user-docu/                  11-page Markdown wiki, images, build-html.java, html/
```

---

## The domain model

Six small types, all immutable:

| Type | Shape |
|---|---|
| `FieldDefinition` | `record(key, labelKey, displayOrder)` — a value, not an enum, so a new field can come from an extension |
| `InvoiceFields` | Catalog of the six built-in definitions plus `ALL` |
| `RecognizedText` | `record(value)` — raw OCR output, null-safe |
| `SourceImage` | `record(origin, image)` — pixels with the file they came from |
| `ExtractedField` | `record(definition, Optional<String> value)` |
| `InvoiceData` | Ordered map of fields keyed by `key`, plus the source text |

Two decisions worth knowing:

- **Missing fields are kept, not dropped.** `InvoiceData` always holds all six,
  so a renderer decides how absence looks (`N/A`, `null`, an empty cell) without
  knowing the catalog.
- **Fields are display-ordered.** `InvoiceData.of(...)` sorts by
  `displayOrder`, so every formatter emits the same order without sorting.

---

## Extraction

Recognition is a **ladder of strategies per field**, not one regular
expression. The first rung that yields a value wins, so a tidy invoice is read
exactly and an awkward one still produces an answer instead of `N/A`:

| Rung | Strategy | Handles |
|---|---|---|
| 1 | Label with the value beside it | `Furnizor: SC ALFA SRL` |
| 2 | Label with the value on one of the next lines | column headings, block layouts |
| 3 | Value recognised by its shape, inside the right part of the page | the supplier's `RO…` code |
| 4 | Value recognised anywhere, or inferred | letterhead names, "the largest amount is the total" |

### What is recognised

| Field | Key | Strategies, in priority order |
|---|---|---|
| Furnizor | `supplier` | Furnizor, Emitent, Vânzător, Prestator — then any company-shaped line in the supplier's block |
| Serie / Numar | `invoiceNumber` | Seria AB nr 1024; Factura (fiscala) nr./numar/no.; Nr. factura (including as a column heading); Seria; Invoice no. |
| Data emiterii | `issueDate` | Data facturii/emiterii; `din <date>`; Data; then the first plausible date on the page |
| CUI / CIF | `fiscalCode` | CUI, CIF, Cod fiscal, Cod de identificare fiscala — scoped to the supplier's block first |
| TVA | `vatAmount` | Total TVA, Valoare TVA, TVA — never `fara TVA` |
| Total de plata | `totalAmount` | Total de plata/general/factura; Total (not `Total fara`); then the largest amount |

### Reading a damaged page

Four mechanisms, each separately tested:

- **Diacritic folding.** `SearchText` keeps the original alongside a folded copy
  of *identical length*, so patterns are written in plain ASCII, match `Vânzător`
  and `fără`, and every offset still points at the same place — the value handed
  back keeps its accents.
- **OCR digit tolerance.** Every digit position accepts `O I l S B Z`, because a
  pattern insisting on `[0-9]` rejects `l.428,OO` outright. `OcrDigits` puts the
  digits back afterwards: wholly for amounts and dates, and only for entirely
  digit-shaped runs in an invoice number, so `ZT-OO91` becomes `ZT-0091` while
  `AB123` and a series like `SB` are left alone. Every pattern also demands at
  least one real digit, so a word can never pass as a number.
- **Region scoping.** An invoice names two companies whose codes look identical;
  only position tells them apart. `DocumentRegions` locates the supplier's block
  — correctly even when the buyer is printed first — and `RegionScopedExtractor`
  confines the search to it.
- **Negative context.** `Total fara TVA` is the *net* line. Bounded look-behind
  (`(?<!fara )TVA`) and look-ahead (`Total(?![ \t]*fara)`) keep that line's
  amount out of the VAT and total fields.

### Two traps worth knowing

**Labels must not span lines.** Label patterns use `[ \t]*`, never `\s*`: `\s`
matches a newline, so the label itself slides onto the next line and the search
window starts a line too far down. Reaching the next line is the *window's* job.
That single mistake caused two of the last three corpus failures.

**Guessing is gated.** The "largest amount is the total" rung only fires when the
page mentions a total, a sum or an invoice at all (`ContextGatedExtractor`), so a
blank scan or OCR noise yields `N/A` rather than a confident wrong number.

### Accuracy

`RecognitionAccuracyTest` scores the rules against
[`InvoiceCorpus`](src/test/java/com/invoiceocr/support/InvoiceCorpus.java): ten
transcribed layouts covering block suppliers, column headings, right-aligned
totals, a buyer-first page, OCR noise, all-caps, diacritics and a bare
letterhead.

| | Read correctly | Reported `N/A` | Wrong value |
|---|--:|--:|--:|
| Before the strategy ladder | 38 / 60 (63%) | 10 | 12 |
| After | **60 / 60 (100%)** | **0** | **0** |

The corpus is a permanent regression guard: a rule tuned for one layout that
breaks another fails the build.


### Priority beats position

Invoices routinely print a subtotal labelled `Total` above the real
`Total de plata`. A single alternation would take whichever label appears first
on the page; `FirstMatchFieldExtractor` tries patterns in order and keeps the
first hit, so the specific label wins wherever it sits. There is a test that
asserts both behaviours side by side.

### Normalisation

| Chain | Steps | Example |
|---|---|---|
| `Normalizers.text()` | collapse whitespace → trim edge punctuation | `SC   EXEMPLU  SRL \|` → `SC EXEMPLU SRL` |
| `Normalizers.code()` | text → uppercase | ` fct-2024/0182 ` → `FCT-2024/0182` |
| `Normalizers.amount()` | text → positional decimal rule | `1.190,00` `1,190.00` `1 190,00` → `1190.00` |
| `Normalizers.date()` | text → day-first, 2-digit year → 20xx | `5.3.24` → `05.03.2024` |
| `Normalizers.fiscalCode()` | text → strip separators, uppercase | `ro 12.345.678` → `RO12345678` |

The amount rule is positional rather than locale-based: the last separator is a
decimal point **only** when exactly two digits follow it; every other separator
is a thousands separator and disappears.

A date that is not plausible — `32.01.2024`, or an ISO-style `2024-03-05` — is
returned **unchanged** rather than reformatted into something wrong. That is a
deliberate signal to check the raw panel.

---

## Export

Seven formats, registered in `ApplicationAssembler.exportService(...)`:

| Format | Extension | Contains | Implementation |
|---|---|---|---|
| PDF | `.pdf` | fields + raw text | `PdfInvoiceExporter` (hand-written) |
| Plain text | `.txt` | fields + raw text | `PlainText` wrapped in `RawTextAppendingFormatter` |
| Markdown | `.md` | fields + raw text | `MarkdownInvoiceReportFormatter` |
| HTML | `.html` | fields + raw text | `HtmlInvoiceReportFormatter` (self-contained, themed) |
| JSON | `.json` | fields only | `JsonInvoiceReportFormatter` |
| XML | `.xml` | fields only | `XmlInvoiceReportFormatter` |
| CSV | `.csv` | fields only | `CsvInvoiceReportFormatter` (RFC 4180, CRLF) |

**The split is a rule, not an accident.** Readable formats carry the raw OCR
text because a person may need to check what the engine saw; data formats carry
the fields alone, under fixed English keys, so a file means the same thing
whatever `ui.locale` says.

### Writing safely

`DefaultInvoiceExportService` writes to `<target>.part` and moves it into place
only when the write completes. A failure halfway through therefore never
truncates a good existing file, and no partial file is left behind. Both are
tested.

### The PDF writer

`PdfInvoiceExporter` emits a conforming PDF 1.4 file by hand: catalog, page
tree, one content stream per page, and the three standard Type 1 fonts every
reader has built in. Roughly 250 lines, and it keeps the jar free of a
multi-megabyte PDF toolkit that would do far more than this report needs.

- Text is real text — searchable and selectable, not an image.
- Long OCR text paginates, and each page carries `Page n of m`.
- Encoding is WinAnsi, which those fonts declare. Romanian letters outside it
  are transliterated (`ă`→`a`, `ș`→`s`, `ț`→`t`) so a PDF is always produced;
  every other format keeps them intact. This is documented for users too.
- `(`, `)` and `\` are escaped, or they would end a PDF string early.

The unit test walks the cross-reference table and asserts every byte offset
points at the object it claims — a wrong offset yields a file that opens blank,
which is exactly the bug a cheap test can catch.

---

## Internationalisation

- `MessageSource` is a one-method interface; `ResourceBundleMessageSource`
  implements it over `ResourceBundle` with `MessageFormat` arguments.
- 45 keys, in two bundles: [`messages.properties`](src/main/resources/messages.properties)
  (English, the base) and [`messages_ro.properties`](src/main/resources/messages_ro.properties)
  (Romanian, the default via `ui.locale=ro`).
- A missing key degrades to the key itself. A translation gap must never take
  the application down.
- Lookup uses `ResourceBundle.Control.getNoFallbackControl`. **Without it**, a
  missing bundle is looked up under the *host's* locale before the base one, so
  asking for Romanian on an English machine can silently yield a third language.
  That bug was real here and is covered by a regression test.

---

## Error handling

```
InvoiceOcrException (unchecked)
├── ConfigurationException          missing tessdata, bad setting value
├── DocumentLoadException           unreadable or undecodable file
│   └── UnsupportedDocumentException  extension not in the allow-list
├── OcrExecutionException           engine failed or returned nothing
└── ExportException                 file could not be written
```

Everything is unchecked so failures travel freely through suppliers, callables
and listeners without every layer re-wrapping them. Each layer translates
foreign exceptions into one of these, and the presenter distinguishes them from
defects: an `InvoiceOcrException` shows its own message, anything else is
wrapped in a generic "unexpected error" so a raw stack-trace message never
reaches a dialog.

Diagnostics are specific on purpose. `TesseractInstallation.verify()` reports
the directory it looked in, the language it wanted, and the `*.traineddata`
files actually present — the difference between a five-second and a one-hour
fix.

---

## Concurrency

OCR takes seconds; doing it on the event dispatch thread freezes the window.
`TaskExecutor` has one method:

```java
<T> void execute(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure);
```

`SwingTaskExecutor` runs the task on a `SwingWorker` and fires both callbacks on
the EDT. `DirectTaskExecutor` runs inline, which is what the presenter tests
use. The presenter itself never mentions a thread.

---

## Testing

154 tests across 20 test classes, plus fakes and the corpus in
`src/test/java/.../support/`. Everything except the Tess4J binding runs without a
native library, because the engine is an interface.

| Area | Class | Notable coverage |
|---|---|---|
| Accuracy | `RecognitionAccuracyTest` | The 10-layout corpus, field by field; refuses to invent values on a non-invoice |
| Page preparation | `SearchTextTest` | Folding preserves length and offsets; values keep their accents; regions confine a search but stay transparent to look-behind |
| Value shapes | `ValuePatternsTest` | Amounts in every convention; dates and percentages rejected as amounts; damaged values still matched |
| Strategies | `ExtractionStrategiesTest` | Value below a heading, line budget, largest-amount selection, gating, supplier-vs-buyer scoping, digit repair |
| Normalisation | `AmountNormalizerTest`, `DateNormalizerTest`, `NormalizersTest` | Every separator convention; implausible dates left alone |
| Extraction | `FirstMatchFieldExtractorTest` | Priority order vs. a naive alternation, side by side |
| | `RuleBasedInvoiceParserTest` | Missing fields kept, first winning rule, display order |
| | `RomanianInvoiceRuleProviderTest` | A full invoice, layout variants, garbage input |
| Pipeline | `DefaultInvoiceRecognitionServiceTest` | Stage order, preprocessed image reaches the engine, engine closed on failure |
| Export | `DefaultInvoiceExportServiceTest` | Temp-file safety, no partial files, unknown format |
| | `PdfInvoiceExporterTest` | xref offsets, page tree, pagination, transliteration, escaping |
| Formats | `StructuredFormattersTest` | XML parsed by a real `DocumentBuilder`; CSV quoting; HTML escaping |
| Presentation | `InvoicePresenterTest` | Export gating, file-name suggestion, failure paths, clear |
| Config / i18n | `ChainedConfigurationSourceTest`, `ConfigurationBackedOcrSettingsTest`, `ResourceBundleMessageSourceTest` | Precedence, blank values, locale fallback |

```bash
mvn test
```

### Without Maven

The project compiles and tests with plain `javac` given the Tess4J jar and the
JUnit 5 jars on the classpath:

```bash
find src/main/java -name '*.java' > sources.txt
javac -encoding UTF-8 -Xlint:all -Werror -d out/main -cp tess4j.jar @sources.txt
find src/test/java -name '*.java' > tests.txt
javac -encoding UTF-8 -d out/test -cp "out/main;junit-jupiter-api.jar;junit-jupiter-params.jar;junit-platform-commons.jar;opentest4j.jar;apiguardian.jar" @tests.txt
```

Then run the JUnit Platform launcher against `out/test`. The main sources
compile clean under `-Xlint:all -Werror`.

---

## Extending it

**Add a field** — declare it in `InvoiceFields`, add a rule to the provider, add
its label to both bundles. Nothing else changes; formatters pick it up from
`displayOrder`, and JSON/XML/CSV pick up the key.

```java
public static final FieldDefinition DUE_DATE =
        new FieldDefinition("dueDate", "field.dueDate", 35);

ExtractionRule.forField(InvoiceFields.DUE_DATE)
        .matching("Scadent[a\u0103]\\s*[:\\-]?\\s*([0-9]{1,2}[./\\-][0-9]{1,2}[./\\-][0-9]{2,4})")
        .normalizedBy(Normalizers.date())
        .build();
```

**Add an export format** — write an `InvoiceReportFormatter`, then register it:

```java
new TextInvoiceExporter(
        ExportFormat.text("yaml", "format.yaml", "yaml"),
        new YamlInvoiceReportFormatter());
```

Add the format to `ExportFormats.ALL` and a `format.yaml` label to both bundles.
The save dialog, the `export.defaultFormat` setting and the service pick it up
with no further change. A binary format implements `InvoiceExporter` directly,
the way `PdfInvoiceExporter` does.

**Support another invoice dialect** — write a second `ExtractionRuleProvider`
and combine: `new SupplierXRules().and(new RomanianInvoiceRuleProvider())`. The
first provider that produces a value for a field wins.

**Swap the OCR engine** — implement `OcrEngine` and `OcrEngineFactory`, override
`ocrEngineFactory(...)` in a subclass of `ApplicationAssembler`.

**Swap the UI** — implement `InvoiceView`, `DocumentChooser`, `ExportChooser`
and `UserNotifier`. `InvoicePresenter` is unchanged, because it imports no
toolkit class.

**Add an interface language** — drop in `messages_xx.properties`. No code
change; `ui.locale=xx` selects it.

---

## The installer

Two ways to install on Windows, both in [installer/](installer/) and documented
in [installer/README.md](installer/README.md):

| | `invoice-ocr.nsi` | `Install-InvoiceOCR.ps1` |
|---|---|---|
| Produces | `invoice-ocr-setup-1.1.1.exe` to hand out | nothing — installs directly |
| Needs | NSIS 3.x to build | nothing |

```powershell
powershell -ExecutionPolicy Bypass -File installer\build-installer.ps1
```

The build script gathers the jar (running Maven if needed), the Tesseract setup
executable, `eng` + `ron` language files, and the generated HTML handbook, then
compiles with `makensis`.

Both installers check for Java 17+, install the bundled Tesseract silently
(`/S`, no `/D`, so it uses its own default location), then **discover where that
landed** rather than assuming: fixed paths first, then every uninstall registry
entry whose display name starts with `Tesseract`, across the 64-bit view, the
32-bit view and the per-user hive, reading `InstallLocation` or the parent of
`UninstallString`. A candidate only counts if it actually contains
`tessdata\*.traineddata`.

That paranoia earned its keep: on the test machine the Tesseract entry has an
**empty `InstallLocation`** and an **unquoted `UninstallString` containing a
space**, so the naive readings both fail.

Bundled language files fill gaps without overwriting existing ones. If no
Tesseract is found at all, a `tessdata` folder is created inside the application
directory — the app still works, because the engine is in the jar.

---

## API documentation

[Doxyfile](Doxyfile) generates a browsable reference for the source, with the
README as its front page:

```bash
doxygen          # from the project root
```

Output lands in `docu/html/index.html` (gitignored). Requires Doxygen and
Graphviz; if graphs come out missing, set `DOT_PATH` to the folder holding
`dot.exe`.

Only settings that differ from Doxygen's defaults are listed, so the file stays
readable. Three of them are load-bearing:

| Setting | Why |
|---|---|
| `GENERATE_DOCBOOK = NO` | With graphs enabled, Doxygen 1.18's DocBook generator asks `dot` to write `<name>.png`, then tries to rename `<name>.dot.png` onto it. That file never exists, so **every** graph fails with `Failed to rename …` — several hundred errors per run — and the DocBook output ends up with no images at all. HTML is unaffected. LaTeX and man are off simply because nothing here needs them |
| `DOT_GRAPH_MAX_NODES = 100` | `ApplicationAssembler` pulls in 53 nodes. Under Doxygen's default ceiling of 50 its include graph is silently dropped with a warning |
| `MARKDOWN_ID_STYLE = GITHUB` | Makes heading anchors match GitHub's, so the README's own table of contents resolves here too. Under the default style the anchors are `autotoc_md0`, `autotoc_md1`, … and every TOC entry is reported unresolvable |

A clean run reports **0 errors**. It does report ~23 warnings, all benign and
none of them a defect:

- README links to files outside Doxygen's input set — the user wiki, the
  resource bundles, the installer. They work on GitHub; Doxygen simply is not
  documenting those files.
- `{@link}` targets in the JDK (`SwingWorker`, `ResourceBundle`, `ITesseract`)
  and a few unqualified cross-package names. Doxygen has no JDK sources and its
  Java `{@link}` resolution is weaker than javadoc's, which resolves all of
  them through the imports.

---

## User documentation

[user-docu/](user-docu/) is an 11-page wiki written for the people who *use* the
application: installation, first invoice, the window, the six fields, exporting,
scan quality, settings, troubleshooting, FAQ, glossary.

The Markdown is the source of truth. [build-html.java](user-docu/build-html.java)
is a single-file, dependency-free generator that turns it into a browsable site
with a sidebar, per-page section nav, client-side search over every heading, and
prev/next paging:

```bash
cd user-docu
java build-html.java
```

---

## Differences from the C++ original

These are fixes, not accidents:

| Original | Here |
|---|---|
| `RO?\s*\d{2,12}` for the fiscal code — means "R" plus an optional "O" | `(?:RO\s*)?[0-9]{2,10}`, prefix optional as a whole |
| `(?:Total de plata\|Total general\|Total)` in one alternation — a bare `Total` earlier on the page wins | Patterns tried in priority order |
| `TVA 19%` captured `19` as the VAT amount | The rate is skipped explicitly |
| Amounts and dates shown as OCR produced them | Normalised to `1234.56` and `dd.MM.yyyy` |
| Tessdata path hard-coded to one developer's machine | Configurable four ways, with a diagnostic when wrong |
| OCR ran on the UI thread; the window froze | Worker thread, progress bar, responsive window |
| `MessageBox` on any failure, one generic message | Typed exception hierarchy; each layer explains its own failure |
| Romanian strings compiled into the code | Resource bundles (`ro`, `en`) |
| No image preparation | Optional upscale and greyscale before recognition |
| No way to get data out | Seven export formats |

---

## What has actually been verified

Stated plainly, because "it compiles" is not the same as "it works":

- **Builds clean.** `mvn clean package` succeeds; main sources compile under
  `-Xlint:all -Werror` with no warnings.
- **154 tests pass**, including a PDF whose cross-reference offsets are checked
  byte by byte and XML parsed by a real `DocumentBuilder`.
- **Recognition is measured, not asserted.** The 10-layout corpus is read
  60/60, up from 38/60, and the corpus runs on every build.
- **The app runs.** Driven end to end against real Tesseract: an invoice loaded
  through the real file dialog, recognised in ~400 ms, 6 of 6 fields extracted.
- **Export works in the real UI.** Export disabled before load, enabled after,
  save dialog offering all seven filters, PDF written, status bar confirming.
- **The PDF renders.** Opened in Chrome's viewer: correct layout, selectable
  text, page footer.
- **Error paths.** A deliberately wrong tessdata path produces the specific
  diagnostic dialog, and the window stays usable.
- **The installer compiles** with NSIS 3.12 into a 47.5 MB setup, and its
  tessdata discovery was tested against the real registry.

**Not verified:** the installer has never been run end to end — the UAC prompt
for the test install was cancelled. The silent Tesseract-install branch is
therefore untested, as is the uninstaller.

---

## Known limitations

- One image per run; no batch or folder mode.
- No PDF *input* — convert to PNG/TIFF first. PDF output is supported.
- Only the six header/total fields; line items are not parsed.
- Field labels are Romanian and English, so other dialects transcribe correctly
  but may not populate fields.
- PDF export transliterates `ă ș ț` (see [Export](#export)).
- The application writes nothing until you export, and keeps no history.
- No deskew, shadow removal or sharpening — scan quality is the user's job, and
  [Preparing Invoices](user-docu/Preparing-Invoices.md) explains how.

---

## Changelog

### 1.1.1 — recognition

The recogniser was rebuilt around a ladder of strategies per field. On the
10-layout corpus this took accuracy from **38/60 with 10 fields reported as
`N/A`** to **60/60 with none**. No interface change and no new setting, so an
existing `invoice-ocr.properties` carries over untouched.

- `SearchText` with length-preserving diacritic folding: patterns are plain
  ASCII, values keep their accents.
- Values are found on the lines *below* a label, which is what column headings
  and block layouts need — the old rules required the same line.
- OCR digit tolerance in every pattern, with `OcrDigits` repairing afterwards.
- `DocumentRegions` + `RegionScopedExtractor`: the supplier's fiscal code is no
  longer the buyer's when the buyer is printed first.
- Negative context so `Total fara TVA` stops being read as the VAT figure.
- Shape-based fallbacks: company-shaped lines, `RO…` anywhere, first plausible
  date, largest amount — the last one gated so a non-invoice is not given
  invented values.
- New `extraction.text` package; `FieldExtractor` now takes a prepared page and
  a region.
- Tests: 91 → 154, including a permanent accuracy corpus.

### 1.1.0

- Export in seven formats: PDF, TXT, Markdown, HTML, JSON, XML, CSV.
- New `export` package, `ExportChooser` contract and Swing save dialog with
  per-format filters and extension correction.
- Dependency-free PDF writer with pagination and page numbering.
- `export.defaultFormat` setting.
- Overwrite confirmation, atomic-ish writes via a temp file.
- New wiki page [Exporting](user-docu/Exporting.md); FAQ, window, settings,
  troubleshooting and glossary pages updated.
- Tests: 62 → 91.

### 1.0.0

- Complete rewrite of the C++/CLI original into 17 packages.
- Configurable tessdata path, four override sources.
- Romanian and English interface.
- Image preprocessing, priority-ordered extraction rules, value normalisation.
- 11-page user wiki and HTML generator.
- NSIS and PowerShell installers.
