# Invoice OCR — User Guide

**Invoice OCR** reads a scanned or photographed invoice and pulls out the six
fields you would otherwise retype by hand: supplier, invoice number, date,
fiscal code, VAT and total due.

You load an image, wait a second or two, and get two panels side by side: the
raw text the recognition engine saw, and a clean summary of the fields it
identified.

![The main window after processing an invoice](images/result.png)

Everything happens on your own computer. No invoice ever leaves the machine.

---

## Where to start

| If you… | Read |
|---|---|
| have not installed it yet | [Installation](Installation.md) |
| want to process your first invoice | [Getting Started](Getting-Started.md) |
| want to know what every button does | [The Main Window](The-Main-Window.md) |
| wonder what the six fields mean | [Extracted Fields](Extracted-Fields.md) |
| want to save the result as PDF, CSV, JSON… | [Exporting](Exporting.md) |
| get poor results | [Preparing Invoices](Preparing-Invoices.md) |
| need Romanian recognition, another language, or other formats | [Settings](Settings.md) |
| hit an error message | [Troubleshooting](Troubleshooting.md) |
| have a quick question | [FAQ](FAQ.md) |
| meet an unfamiliar term | [Glossary](Glossary.md) |

---

## In one minute

1. Start the application. The window opens with the status **Pregatit** (*Ready*).
2. Click **Incarca factura** (*Load invoice*).
3. Pick a picture of an invoice — PNG, JPG, BMP, TIFF or GIF.
4. Wait for the progress bar to stop.
5. Read the right-hand panel. The bottom of it tells you how many of the six
   fields were found, for example `Campuri identificate: 6 din 6`.
6. Click **Exporta** (*Export*) to save the result as PDF, TXT, Markdown, HTML,
   JSON, XML or CSV.

Fields the recognition could not read clearly are shown as **N/A** rather than
guessed. If you see several of them, [Preparing Invoices](Preparing-Invoices.md)
is the page that fixes it most often.

---

## What this application does not do

Knowing the limits up front saves time:

- It reads **one image at a time**. There is no batch or folder mode.
- It does **not open PDF files** directly — convert a page to PNG or TIFF first.
  (It can *write* PDF: see [Exporting](Exporting.md).)
- It does **not correct** what the recognition engine reads. If the scan is
  poor, the result is poor — the raw panel always shows you exactly what was read.
- It reads **totals and headers**, not the individual line items of the table.
- It keeps **no history**. Export what you need before loading the next invoice.

---

*Applies to Invoice OCR 1.1.1. Interface language is Romanian by default;
see [Settings](Settings.md#interface-language) to switch to English.*
