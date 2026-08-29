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
| Right panel | **Date extrase (structurat)** | Extracted data (structured) | The six fields, cleaned up |
| Status bar | **Pregatit** | Ready | Current state, bottom left |

---

## The status bar

The bottom-left line is the fastest way to see what the application is doing.

| Message (Romanian) | Meaning |
|---|---|
| `Pregatit` | Idle, waiting for you |
| `Se proceseaza factura.png...` | Reading that file now |
| `factura.png: 6 campuri identificate` | Finished; 6 of 6 fields found |
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

Always the same six lines in the same order, whether or not each was found,
followed by a count and a reminder about `N/A`. A consistent shape means you can
scan the same position every time instead of hunting.

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
