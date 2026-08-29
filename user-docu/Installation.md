[Home](Home.md) › **Installation**

# Installation

Three things have to be in place: **Java**, the **application**, and the
**language data** the recognition engine needs. The third one is the step people
miss, and it is the one that produces the most support questions.

---

## 1. Check that Java is installed

Invoice OCR needs **Java 17 or newer**. Open a terminal (Windows: press `Win`,
type `cmd`, press Enter) and run:

```
java -version
```

You should see a version of 17 or higher, for example `openjdk version "21.0.9"`.

If the command is not recognised, install a Java runtime — for example
[Adoptium Temurin](https://adoptium.net) 21 — then close and reopen the terminal
and check again.

---

## 2. Get the application

**If you were given a ready-made file**, you have `invoice-ocr.jar`. Put it
anywhere you like, for example `C:\Programe\InvoiceOCR\`.

**If you have the source code**, build it once:

```
mvn clean package
```

The result is `target/invoice-ocr.jar`. It already contains everything the
application needs except the language data below.

---

## 3. Install the language data

The recognition engine needs a **`tessdata` folder** containing one
`*.traineddata` file per language. Without it the application starts normally but
fails the moment you load an invoice, with the message
`tessdata directory not found`.

### Option A — install Tesseract (recommended on Windows)

Install the Tesseract package, which brings the folder with it:

```
winget install UB-Mannheim.TesseractOCR
```

During installation, tick the **Romanian** language if you process Romanian
invoices. The folder you end up with is:

```
C:\Program Files\Tesseract-OCR\tessdata
```

That is the path the application expects by default, so if you install here you
have nothing further to configure.

### Option B — download only the language files

If you prefer not to install anything, create a folder such as
`C:\tessdata` and download the files you need into it from the
[tessdata_fast repository](https://github.com/tesseract-ocr/tessdata_fast):

| Language | File | Needed for |
|---|---|---|
| English | `eng.traineddata` | English invoices, and a good general fallback |
| Romanian | `ron.traineddata` | Romanian invoices, diacritics included |

Each file is a few megabytes. Then tell the application where the folder is —
see step 4.

> **Which variant?** `tessdata_fast` is quick and accurate enough for clean
> scans. If results are disappointing on difficult documents, the same files
> from `tessdata_best` are slower but more accurate.

---

## 4. Point the application at the folder

Only necessary if you did *not* use the default Windows location.

The simplest way is to create a file named **`invoice-ocr.properties`** in the
same folder as `invoice-ocr.jar`, containing:

```properties
ocr.tessdata.path=C:/tessdata
ocr.language=ron+eng
```

Use forward slashes. This file survives application updates, because it sits
next to the jar rather than inside it. Two other ways to do the same thing are
described in [Settings](Settings.md#where-settings-live).

---

## 5. Start it

Double-click `invoice-ocr.jar`, or run:

```
java -jar invoice-ocr.jar
```

The window opens with two empty panels and the status **Pregatit** (*Ready*).
That is a successful installation — the language data is only checked when you
actually load an invoice.

To be sure everything works end to end, process one invoice now:
[Getting Started](Getting-Started.md).

---

## Optional: a desktop shortcut

Right-click your desktop → **New → Shortcut**, and enter:

```
javaw -jar "C:\Programe\InvoiceOCR\invoice-ocr.jar"
```

Using `javaw` instead of `java` starts the window without a black console
window behind it.

---

## Other operating systems

The application runs on macOS and Linux with the same Java command. Only the
tessdata location differs — typically `/usr/share/tesseract-ocr/*/tessdata` on
Linux and `/opt/homebrew/share/tessdata` on macOS. Set `ocr.tessdata.path`
accordingly.

---

**Next:** [Getting Started](Getting-Started.md) ·
**If something went wrong:** [Troubleshooting](Troubleshooting.md)
