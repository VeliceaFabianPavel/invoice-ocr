[Home](Home.md) › **Glossary**

# Glossary

Terms you will meet in this guide, in the application, and in its error
messages.

---

### (?)

The mark beside a value the application worked out or guessed rather than read
from its own label. It is the application saying which figures are worth a glance
at the raw panel. See [How It Reads](How-It-Reads.md#it-tells-you-what-to-check).

### Checksum / control digit

An extra digit computed from the others, so that a number can be checked against
itself. Romanian fiscal codes carry one; every IBAN carries one. It is what lets
the application tell a code it read correctly from one it did not. See
[How It Reads](How-It-Reads.md#it-checks-what-it-read).

### CUI / CIF

*Cod Unic de Înregistrare* / *Cod de Identificare Fiscală* — the Romanian fiscal
identification code of a company, often printed with an `RO` prefix for
VAT-registered businesses. The application shows it as `RO12345678`.

### dpi (dots per inch)

Scanning resolution. **300 dpi** is the recommendation throughout this guide:
below it, thin characters break up; far above it, files grow without improving
recognition.

### Engine mode (OEM)

Which internal recognition engine is used. Setting `ocr.engineMode`; leave at the
default unless advised otherwise. See [Settings](Settings.md#engine-mode).

### Export

Saving the extracted data as a file — PDF, TXT, Markdown, HTML, JSON, XML or
CSV. Nothing is written to disk until you export. See [Exporting](Exporting.md).

### Greyscale

An image with shades of grey but no colour. The application converts to
greyscale automatically before recognition, because it makes text stand out from
the background more reliably.

### JAR (`.jar`)

The single file the whole application is packaged in — `invoice-ocr.jar`. Run it
by double-clicking, or with `java -jar invoice-ocr.jar`.

### Java

The runtime the application needs. Version 17 or newer. Check with
`java -version`.

### Line item

One row of the goods table — what was sold, how much of it, at what price. Read
since version 1.2. See [Line Items](Line-Items.md).

### N/A

Shown instead of a value when a field could not be found. The application marks
it as unknown rather than guessing. See
[When fields show N/A](Getting-Started.md#when-fields-show-na).

### Net amount

The invoice total before VAT is added — `Total fara TVA` on a Romanian invoice.
The application reads it, or works it out, since net + VAT = total.

### Normalisation

Rewriting a recognised value into one consistent form: `1.190,00` and
`1,190.00` both become `1190.00`. See
[How values are cleaned up](Extracted-Fields.md#how-values-are-cleaned-up).

### OCR (Optical Character Recognition)

Turning a picture of text into text a computer can process. It is the core of
what this application does, and it is never perfect — which is why the raw text
is always shown next to the results.

### Page segmentation mode (PSM)

How the engine divides a page into blocks of text before reading them. Setting
`ocr.pageSegmentationMode`. See
[Settings](Settings.md#page-segmentation-mode).

### Pass

One reading of the page: one preparation of the picture, one assumption about
the layout. The application makes up to four and compares the answers. See
[Settings](Settings.md#how-many-times-a-page-is-read).

### Preprocessing

The automatic preparation applied to your image before recognition: enlarging
small scans, converting to greyscale and — since version 1.2 — straightening,
stretching the contrast, converting to black and white and sharpening, each on a
different attempt. See
[What the application does for you](Preparing-Invoices.md#what-the-application-does-for-you).

### Properties file

A plain-text settings file with one `name=value` per line. Yours is
`invoice-ocr.properties`, next to the jar. See
[Settings](Settings.md#where-settings-live).

### Raw text

The unmodified output of the recognition engine, shown in the left panel. Your
evidence when a result looks wrong.

### Skew

The angle a page ended up at when it was scanned or photographed. Even two or
three degrees costs accuracy, because slightly slanted lines of text get merged
with their neighbours. The application measures it and straightens the page. See
[How It Reads](How-It-Reads.md#it-reads-the-page-several-times).

### Tesseract

The open-source recognition engine the application uses internally. You do not
interact with it directly; you only need to supply its language data.

### tessdata

The folder holding the language data files. Its location is the
`ocr.tessdata.path` setting — the single most common thing to get wrong.

### traineddata (`*.traineddata`)

One file per language, for example `ron.traineddata` for Romanian, telling the
engine what that language's characters and words look like.


### TVA

*Taxa pe Valoarea Adăugată* — Romanian value-added tax (VAT). The application
extracts the VAT **amount**, not the rate: from `Total TVA 19% 190,00` it takes
`190.00`. It also checks it: net + VAT has to equal the total.

---

**See also:** [FAQ](FAQ.md) · [Settings](Settings.md)
