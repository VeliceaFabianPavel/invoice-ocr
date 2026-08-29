[Home](Home.md) › **Settings**

# Settings

Everything configurable lives in plain-text settings. There is no options dialog
in the application — this is deliberate: settings are set once per installation,
usually by whoever installs it.

---

## Where settings live

You can set a value in four places. If the same setting appears in more than
one, the **higher row wins**.

| # | Where | How you set it | Use it for |
|---|---|---|---|
| 1 | Command line | `java -Docr.language=ron -jar invoice-ocr.jar` | Trying something out once |
| 2 | Environment variable | `OCR_LANGUAGE=ron` | Rolling out one value across many machines |
| 3 | **`invoice-ocr.properties`** next to the jar | a text file you create | **The normal choice** |
| 4 | Built-in defaults | inside the application | Fallback if you set nothing |

The recommended approach is a file named exactly `invoice-ocr.properties` in the
same folder as `invoice-ocr.jar`:

```properties
ocr.tessdata.path=C:/tessdata
ocr.language=ron+eng
ui.locale=ro
```

Use **forward slashes** in paths, even on Windows. Lines starting with `#` are
comments. Restart the application after changing the file.

Environment variables use the same name in capitals with underscores:
`ocr.tessdata.path` becomes `OCR_TESSDATA_PATH`.

---

## Complete reference

| Setting | Default | What it does |
|---|---|---|
| `ocr.tessdata.path` | `C:/Program Files/Tesseract-OCR/tessdata` | Folder holding the `*.traineddata` language files. **The one setting you must get right.** |
| `ocr.language` | `eng` | Which language(s) to recognise. Combine with `+` |
| `ocr.pageSegmentationMode` | `3` | How the page is divided into text blocks |
| `ocr.engineMode` | `3` | Which recognition engine variant to use |
| `document.supportedExtensions` | `png,jpg,jpeg,bmp,tif,tiff,gif` | Formats offered in the file dialog |
| `image.preprocessing.enabled` | `true` | Whether images are prepared before recognition |
| `image.preprocessing.minimumWidth` | `1000` | Images narrower than this are enlarged |
| `export.defaultFormat` | `pdf` | Format the export dialog opens on |
| `ui.locale` | `ro` | Interface language: `ro` or `en` |

---

## Recognition language

`ocr.language` must match the language **printed on your invoices**, and each
language needs its file in the tessdata folder.

```properties
ocr.language=ron        # Romanian invoices
ocr.language=ron+eng    # Romanian invoices containing English terms
ocr.language=eng        # English invoices
```

`ron+eng` is the best default for Romanian business invoices: many carry English
product names, and the combination handles both. It is slightly slower than a
single language.

If the file is missing, the application tells you exactly which one it wanted and
what it found instead — see
[Troubleshooting](Troubleshooting.md#language-file-not-found).

---

## Page segmentation mode

`ocr.pageSegmentationMode` tells the engine how the page is laid out. Only a few
values are worth trying:

| Value | Meaning | When to use |
|---|---|---|
| `3` | Fully automatic *(default)* | Almost always |
| `4` | A single column of text of varying sizes | Narrow receipts, simple invoices |
| `6` | One uniform block of text | A cropped region, e.g. just the totals |
| `11` | Sparse text, no particular order | Chaotic layouts where `3` finds nothing |

Change this only after a good scan still gives poor results.

---

## Engine mode

`ocr.engineMode` selects the recognition engine:

| Value | Meaning |
|---|---|
| `3` | Whatever the installation provides *(default)* |
| `1` | Neural network only — usually the most accurate on modern data files |
| `0` | Legacy engine — only with older language files |

Leave it at `3` unless you have a specific reason.

---

## Document formats

`document.supportedExtensions` controls what the file dialog offers and what the
application will open. Comma-separated, dots optional:

```properties
document.supportedExtensions=png,tif,tiff
```

Restricting the list is a practical way to stop colleagues loading low-quality
JPEGs. Adding a format only helps if it is one the image libraries can decode —
adding `pdf` here does **not** enable PDF support.

---

## Image preparation

```properties
image.preprocessing.enabled=true
image.preprocessing.minimumWidth=1000
```

With preparation enabled, small images are enlarged (up to 4×) and everything is
converted to greyscale before recognition. Setting `minimumWidth=0` keeps
greyscale conversion but disables enlargement. Setting `enabled=false` passes
your image through untouched, which is only sensible if you pre-process scans
yourself.

---

## Default export format

Which format the save dialog opens on. You can still pick any other format in
the dialog itself; this only decides the starting point.

```properties
export.defaultFormat=pdf     # pdf, txt, md, html, json, xml or csv
```

If you always feed a spreadsheet, `csv` saves a click each time. An unrecognised
value falls back to `pdf` rather than stopping the application, and the
mistake is written to the log. See [Exporting](Exporting.md) for what each
format contains.

---

## Interface language

```properties
ui.locale=ro    # Romanian (default)
ui.locale=en    # English
```

This changes button labels, panel titles and messages. Any other value falls
back to English. It does **not** affect recognition — that is `ocr.language`.

---

## Worked examples

**A Romanian office, Tesseract installed normally**

```properties
ocr.language=ron+eng
```

Nothing else: the default tessdata path is already correct.

**Language files in a shared folder, English interface**

```properties
ocr.tessdata.path=//fileserver/shared/tessdata
ocr.language=ron+eng
ui.locale=en
```

**Testing a setting without committing to it**

```
java -Docr.pageSegmentationMode=4 -jar invoice-ocr.jar
```

---

**See also:** [Installation](Installation.md) ·
[Troubleshooting](Troubleshooting.md)
