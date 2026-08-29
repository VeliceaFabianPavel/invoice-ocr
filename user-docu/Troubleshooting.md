[Home](Home.md) › **Troubleshooting**

# Troubleshooting

Every failure shows a dialog titled **Eroare** (*Error*) with a specific
message, and the status bar reads `Procesare esuata pentru …`. Find your message
below.

---

## Error messages

### tessdata directory not found

```
tessdata directory not found: D:\facturi\tessdata
```

**Cause.** The folder in `ocr.tessdata.path` does not exist — most often because
the language data was never installed, or the path has a typo.

**Fix.** Check that the folder exists and contains files ending in
`.traineddata`. Then either install the language data
([Installation, step 3](Installation.md#3-install-the-language-data)) or correct
the path ([Settings](Settings.md#where-settings-live)). Use forward slashes:
`C:/tessdata`, not `C:\tessdata`.

---

### Language file not found

```
Language file ron.traineddata not found in C:\tessdata. Available: eng.traineddata
```

**Cause.** The folder exists, but not the language you asked for. The message
lists what *is* there.

**Fix.** Either download the missing file into that folder, or change
`ocr.language` to one that is available. Note that `ron+eng` requires **both**
files.

---

### Unsupported document format

```
Unsupported document format: factura.pdf
```

**Cause.** The file is not one of the accepted picture formats — usually a PDF.

**Fix.** Convert the page to PNG or TIFF at 300 dpi; see
[PDF invoices](Preparing-Invoices.md#pdf-invoices).

---

### File does not exist or cannot be read

```
File does not exist or cannot be read: N:\scanari\factura.png
```

**Cause.** The file was moved, renamed or deleted after you selected it, a
network drive is disconnected, or you lack permission.

**Fix.** Reopen the dialog and select the file again. For network folders, check
the drive is still connected. Copy the file to your local disk to rule out
permissions.

---

### No image decoder available

```
No image decoder available for: factura.tif
```

**Cause.** The extension is accepted, but the file's internal format is not —
for instance an exotic TIFF compression, or a file that is not really an image
despite its name.

**Fix.** Open it in an image viewer and re-save it as PNG.

---

### OCR produced no text

```
OCR produced no text for factura.png
```

**Cause.** The engine ran and found nothing at all. The page is blank, entirely
black, far too low-resolution, or upside down.

**Fix.** Open the image and confirm it really shows a readable invoice the right
way up. Then re-scan at 300 dpi — see [Preparing Invoices](Preparing-Invoices.md).

---

### Could not write the file

```
Could not write factura-03.pdf: Access is denied
```

**Cause.** The export could not be saved: the folder is read-only, a network
drive is disconnected, the disk is full, or the file is open in another program
(a PDF held open by a reader is the usual one).

**Fix.** Close the file if it is open elsewhere, or export to another folder —
your Documents folder is always a safe bet. A file that already existed at that
name is left untouched when an export fails, so nothing good is lost.

---

### Load an invoice first

```
Incarca mai intai o factura: nu exista date de exportat.
```

**Cause.** **Exporta** was used before an invoice had been read, or after
**Goleste** cleared the panels.

**Fix.** Load an invoice; the export button turns active by itself once there
is something to save.

---

### Unexpected error

```
Eroare neasteptata: ...
```

**Cause.** Something outside the normal failures — for example the machine ran
out of memory on a very large image.

**Fix.** Try a smaller or lower-resolution copy. If it recurs on a specific
file, report it — see [Reporting a problem](#reporting-a-problem).

---

## The application will not start

**Nothing happens when I double-click the jar.**
Java is either missing or not associated with `.jar` files. Open a terminal in
the folder and run `java -jar invoice-ocr.jar` — you will see the real reason.

**`java: command not found` or `is not recognized`.**
Java is not installed or not on the system path.
See [Installation, step 1](Installation.md#1-check-that-java-is-installed).

**`UnsupportedClassVersionError`.**
Your Java is older than 17. Check with `java -version` and install a newer
runtime.

**A window flashes and disappears.**
Start it from a terminal so the message stays on screen.

---

## Poor or wrong results

The left panel is the diagnosis tool: **it shows exactly what the engine read**.
Compare it against the invoice.

### Most fields are N/A

The application tries several approaches per field, including values printed
below their labels and values with no label at all, so widespread `N/A` almost
always means the text itself is unreadable rather than the layout being unusual.

Look at the raw panel:

- **Is the text garbled or full of strange characters?** The image quality is
  the problem, and it is by far the most common cause →
  [Preparing Invoices](Preparing-Invoices.md).
- **Is the text correct but the diacritics wrong** (`Furnizor` read as
  `Fumizor`)? The recognition language is wrong → set `ocr.language=ron+eng`.
- **Is the raw panel empty or nearly so?** The page was not read at all: check it
  is the right way up and scanned at 300 dpi.
- **Is the text perfect?** Then the invoice uses labels the application does not
  know → [How a field is found](Extracted-Fields.md#how-a-field-is-found).
  Note the supplier, date, fiscal code and total each have a label-free fallback,
  so a perfect page rarely leaves them empty.

### The total is wrong

Usually the invoice labels its subtotal `Total de plata` and its real total
something else, or an amount was misread. Check the raw panel: if it shows
`1.190,00`, the extraction is right; if it shows `1.19O,00` (letter O for zero),
it is a recognition problem and a better scan fixes it.

### The date looks odd

Dates are shown as `dd.MM.yyyy`. If a value appears **unchanged** in an unusual
form, the application judged it implausible and refused to reformat it — for
example a misread `32.01.2024`, or an ISO-style `2024-03-05`, which is not the
day-first format expected on Romanian invoices.

### Only one or two fields are found on a good scan

Check that the whole page was captured, especially the totals block at the
bottom. A cut-off footer is the classic cause.

---

## Performance

Recognition normally takes one to three seconds per page.

- **Much slower?** Very large images (above ~4000 pixels wide) take longer for no
  benefit — scan at 300 dpi rather than 1200.
- **Using several languages** (`ron+eng`) is slower than one.
- The `tessdata_best` language files are noticeably slower than `tessdata_fast`.

---

## Reporting a problem

When asking for help, include:

1. the **exact** error message from the dialog,
2. the **whole content of the left panel** (click in it, `Ctrl+A`, `Ctrl+C`),
3. your `invoice-ocr.properties`, or the settings you use,
4. the invoice image if you are allowed to share it,
5. the output of `java -version`.

Items 1 and 2 identify most problems on their own.

---

**See also:** [Preparing Invoices](Preparing-Invoices.md) ·
[Settings](Settings.md) · [FAQ](FAQ.md)
