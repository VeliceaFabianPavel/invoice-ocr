[Home](Home.md) › **The Main Window**

# The Main Window

The whole application is one window. There are no menus, no tabs and no hidden
dialogs.

![The main window, annotated below](images/main-window.png)

---

## Everything on screen

| Element | Romanian (default) | English | What it does |
|---|---|---|---|
| Title bar | OCR Facturi — Extragere date structurate | Invoice OCR — Structured data extraction | — |
| First button | **Incarca factura** | Load invoice | Opens the file dialog and processes what you choose |
| Second button | **Exporta** | Export | Saves the extracted data as a file. Greyed out until an invoice has been read — see [Exporting](Exporting.md) |
| Third button | **Goleste** | Clear | Empties both panels; changes nothing on disk |
| Progress bar | *(no label)* | | Visible only while an invoice is being read |
| Left panel | **Text OCR brut** | Raw OCR text | What the recognition engine read, unmodified |
| Right panel | **Date extrase (structurat)** | Extracted data (structured) | The twelve fields and the goods table, cleaned up |
| Status bar | **Pregatit** | Ready | Current state, bottom left |

---

## The status bar

The bottom-left line is the fastest way to see what the application is doing.

| Message (Romanian) | Meaning |
|---|---|
| `Pregatit` | Idle, waiting for you |
| `Se proceseaza factura.png...` | Reading that file now |
| `factura.png: 12 campuri identificate` | Finished; all twelve fields found, and nothing needs checking |
| `factura.png: 10 campuri identificate, 2 de verificat` | Finished; ten found, two of them worked out rather than read — see [How It Reads](How-It-Reads.md) |
| `Se exporta factura.pdf...` | Writing an export file |
| `Exportat in factura.pdf` | The export was written |
| `Procesare esuata pentru factura.png` | Failed; an error dialog explains why |

After a failure the panels keep whatever they showed before, so you never lose a
previous result to a mistyped file name.

---

## The two panels

### Left — raw text

A faithful transcript in a fixed-width font, blank lines and all. Nothing is
corrected here. Use it to judge the quality of a scan: if the raw panel is
garbled, no amount of configuration will produce good fields, and the answer is
a better image — see [Preparing Invoices](Preparing-Invoices.md).

### Right — structured data

Always the same twelve lines in the same order, whether or not each was found,
then the rows of the goods table, then a count and a reminder about `N/A`. A
consistent shape means you can scan the same position every time instead of
hunting.

A value followed by **(?)** was worked out or guessed rather than read from its
own label — the largest amount on a page that never labelled a total, say, or a
VAT figure derived from the other two. It is usually right; it is simply the one
to glance at the left panel about. When any value carries the mark, a short note
under the count explains it.

The status bar counts them for you, so you rarely have to hunt for the marks
yourself.

Both panels are **read-only**. You cannot fix a value by typing over it — and
that is deliberate, so what you copy, or export, is always what the application
actually read.

---

## While it works

During recognition the buttons are disabled, the progress bar animates and the
pointer becomes an hourglass. This is normal and lasts a second or two.

You can still move, resize and maximise the window. The application never
freezes: recognition runs in the background, not in the interface.

---

## Layout

- **Drag the divider** between the panels to give one side more room.
- **Resize or maximise** the window; the panels grow with it.
- **Scroll bars** appear automatically for long invoices, vertically and
  horizontally.

Window size and divider position are not remembered between sessions.

---

## Keyboard

| Keys | Effect |
|---|---|
| `Tab` | Move between the buttons |
| `Space` / `Enter` | Press the focused button |
| `Ctrl+A` | Select all text in the focused panel |
| `Ctrl+C` | Copy the selection |
| `Alt+F4` | Close the application |

---

## Interface language

The interface ships in **Romanian** (default) and **English**. Switch with the
`ui.locale` setting — see [Settings](Settings.md#interface-language). This
changes only the labels you see; it has nothing to do with the language of the
invoices, which is `ocr.language`.

---

**Next:** [Extracted Fields](Extracted-Fields.md) ·
**See also:** [Settings](Settings.md)
