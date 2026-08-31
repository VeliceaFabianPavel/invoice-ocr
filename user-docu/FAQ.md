[Home](Home.md) › **FAQ**

# Frequently Asked Questions

---

### Are my invoices sent anywhere?

**No.** Recognition runs entirely on your computer. The application makes no
network connections and needs no internet access after installation. Invoices
never leave the machine.

### Does it save my invoices, or keep a history?

Only when you ask it to. Nothing is written until you click **Exporta** and
choose a file; there is no history, no database and no cache. Close the window
and nothing remains, so export what you need before loading the next invoice.

### Can it read PDF invoices?

Not directly. Convert the page to PNG or TIFF at 300 dpi first — see
[PDF invoices](Preparing-Invoices.md#pdf-invoices).

### Can it process a whole folder at once?

No. It handles one image per run. For a batch, load them one after another; the
file dialog reopens in the last folder used, so each additional invoice is two
clicks.

### Can I export to Excel or CSV?

Yes. Click **Exporta** and choose **CSV spreadsheet**. The header row is the
same every time, so exported invoices stack into one sheet: paste each new row
under the last. See [Exporting](Exporting.md).

### Which export format should I use?

PDF to file or send to a person, CSV for a spreadsheet, JSON or XML to import
into another program, TXT or Markdown to paste into notes or an e-mail. The
[Exporting](Exporting.md) page has a table comparing all seven.

### Why are the Romanian letters wrong in my PDF?

PDF's built-in fonts cannot show `ă`, `ș` and `ț`, so those become `a`, `s` and
`t` in PDF exports only. Every other format keeps them exactly as recognised.

### Can I correct a value in the panel?

No, both panels are read-only, on purpose: what you copy — or export — is always
exactly what the application read. Correct values afterwards in your own system.

### Why is the total shown as `1190.00` and not `1.190,00`?

Amounts are normalised to a single format — dot for decimals, no thousands
separator — so results are consistent across invoices and paste cleanly into
spreadsheets and accounting imports. The original form is always visible in the
left panel.

### Why does a field show N/A when I can clearly read it on the invoice?

You are reading the invoice; the application reads the recognition output.
Compare with the left panel — the value is usually garbled there. If the raw text
is perfect, the invoice uses a label the application does not know, listed in
[Extracted Fields](Extracted-Fields.md#which-labels-are-recognised).

### Can it handle invoices in other languages?

Yes, for recognition: install the matching `*.traineddata` file and set
`ocr.language`. The **field labels** it searches for, however, are Romanian and
English, so a German invoice will be transcribed correctly but its fields may not
be identified.

### Can I add a thirteenth field?

Not through settings — it requires a small change in the application itself.
The developer documentation in the project's `README.md` describes exactly what
to add.

### Does it work offline?

Yes, completely. Only the initial download of the language files needs internet.

### Does it need Tesseract installed separately?

Only the **language data** (`*.traineddata`). The recognition engine itself is
bundled with the application. Installing Tesseract is simply the most convenient
way to obtain that data on Windows — see
[Installation](Installation.md#3-install-the-language-data).

### Which invoice layouts does it work best with?

Machine-printed invoices with clear labels — `Furnizor:`, `CUI:`,
`Total de plata` — scanned at 300 dpi. Handwritten invoices and stamps over text
still give markedly worse results. Photographs taken at a slight angle are much
better handled since version 1.2, which straightens the page before reading it.

### Can several people use it at once?

Yes; it runs independently on each machine and shares nothing. A `tessdata`
folder on a network share can be used by all of them — set `ocr.tessdata.path`
to the share.

### How much does recognition cost?

Nothing. There is no subscription and no per-page charge; all processing is
local.

### What does the (?) beside a value mean?

That the application worked the value out rather than reading it from its own
label — a VAT figure derived from the net and the total, say, or the largest
amount on a page that never labelled one. It is usually right; it is the one to
check against the left panel before you use it. The status bar counts them for
you. See [How It Reads](How-It-Reads.md).

### Why is it sometimes slower than it used to be?

Because a page it could not read well the first time is now read again, up to
three more times, with the picture prepared differently. A clean scan still
takes one pass and the same second or two it always did. If you would rather
have the old speed unconditionally, set `ocr.passes.maximum=1` — see
[Settings](Settings.md#how-many-times-a-page-is-read).

### It found a VAT amount that is not printed on my invoice. Is that safe?

Yes. Every invoice obeys `net + VAT = total`, so knowing two of the three gives
the third exactly. The application never invents a figure — anything it fills in
is implied by two figures that were actually printed. Derived values are marked
**(?)** so you can see which ones they are.

### Why is the bank account blank when the invoice clearly shows one?

Because it did not pass its checksum. Every IBAN carries one, and an
almost-right bank account is worse than none, so the application reports nothing
rather than something it cannot verify. Check the left panel: the account is
usually there, with one character misread.

### Does it read the product rows?

Yes, since version 1.2, when the table has a recognisable heading row. See
[Line Items](Line-Items.md).

---

**See also:** [Troubleshooting](Troubleshooting.md) · [Glossary](Glossary.md)
