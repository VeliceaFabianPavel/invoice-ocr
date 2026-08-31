# Invoice OCR — User Guide

**Invoice OCR** reads a scanned or photographed invoice and pulls out the twelve
fields you would otherwise retype by hand — supplier, buyer, invoice number,
issue and due dates, fiscal code, trade register, bank account, net amount, VAT,
total due and currency — along with the rows of the goods table.

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
| wonder what the twelve fields mean | [Extracted Fields](Extracted-Fields.md) |
| want the product rows, not just the totals | [Line Items](Line-Items.md) |
| want to know how much to trust a figure | [How It Reads](How-It-Reads.md) |
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
5. Read the right-hand panel. The bottom of it tells you how many of the twelve
   fields were found, for example `Campuri identificate: 10 din 12`.
6. Check anything marked **(?)** — see below.
7. Click **Exporta** (*Export*) to save the result as PDF, TXT, Markdown, HTML,
   JSON, XML or CSV.

Fields the recognition could not read clearly are shown as **N/A** rather than
guessed. If you see several of them, [Preparing Invoices](Preparing-Invoices.md)
is the page that fixes it most often.

---

## New in version 1.2

The application now reads each page **several times over**, preparing the picture
differently each time, and compares the answers — which rescues photographs taken
at an angle, pages lit unevenly, and faded receipts. It stops as soon as one
reading is good enough, so a clean scan is no slower than before.

It also **checks** what it read. A fiscal code has a control digit; a bank
account has a checksum; a date has to exist in the calendar; and net plus VAT has
to equal the total. Where a check fails, the application tries again rather than
reporting the first thing it found — and where two figures are known, the third
is worked out exactly.

What you will notice day to day:

- **Twelve fields instead of six**, and the rows of the goods table.
- **A status line that says what to check**: *"10 fields recognised, 2 to
  check"*, with those two marked **(?)** in the report. Everything unmarked was
  read from its own label.
- **Fewer N/As**, particularly for amounts: an invoice that prints only two of
  the three now yields all three.

[How It Reads](How-It-Reads.md) explains all of it.

---

## What this application does not do

Knowing the limits up front saves time:

- It reads **one image at a time**. There is no batch or folder mode.
- It does **not open PDF files** directly — convert a page to PNG or TIFF first.
  (It can *write* PDF: see [Exporting](Exporting.md).)
- It does **not correct** what the recognition engine reads. If the scan is
  poor, the result is poor — the raw panel always shows you exactly what was read.
- The goods table needs a **recognisable heading row**; without one the rows are
  reported as absent rather than guessed at.
- It keeps **no history**. Export what you need before loading the next invoice.

---

*Applies to Invoice OCR 1.2.0. Interface language is Romanian by default;
see [Settings](Settings.md#interface-language) to switch to English.*
