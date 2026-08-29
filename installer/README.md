# Installer

Two ways to install Invoice OCR on a Windows machine, both of which also put
the Tesseract OCR engine in place.

| | [`invoice-ocr.nsi`](invoice-ocr.nsi) | [`Install-InvoiceOCR.ps1`](Install-InvoiceOCR.ps1) |
|---|---|---|
| Produces | one `invoice-ocr-setup-1.1.1.exe` to hand out | nothing — it installs directly |
| Needs to build | NSIS 3.x | nothing |
| Best for | shipping to other people | installing here and now, or from a script |

Both do the same work: check Java, install Tesseract silently, find where it
landed, copy the application, write settings, create shortcuts, and register
under Programs and Features.

---

## Building the setup executable

```powershell
powershell -ExecutionPolicy Bypass -File build-installer.ps1
```

The script gathers everything first and says what it found, so a failure names
the missing ingredient instead of erroring inside NSIS:

1. **`target\invoice-ocr.jar`** — runs `mvn clean package` if it is missing.
2. **`tesseract-ocr-w64-setup-*.exe`** — looked for in `payload\`, in your
   `Downloads` folder, and next to the script. Override with
   `-TesseractSetup <path>`.
3. **`eng.traineddata` and `ron.traineddata`** — downloaded into
   `payload\tessdata\` if not already there. Use `-SkipLanguageDownload` for an
   offline build.
4. **`..\user-docu\html`** — bundled as the in-application handbook when present.
5. **`makensis.exe`** — from the usual install folders or `PATH`. Override with
   `-MakeNsis <path>`.

NSIS itself: `winget install NSIS.NSIS`.

The result is `invoice-ocr-setup-1.1.1.exe` in this folder.

---

## Installing without building anything

```powershell
powershell -ExecutionPolicy Bypass -File Install-InvoiceOCR.ps1
```

It relaunches itself elevated if needed. Useful switches:

| Switch | Effect |
|---|---|
| `-InstallDir <path>` | Install somewhere other than `C:\Program Files\Invoice OCR` |
| `-TesseractSetup <path>` | Use a specific Tesseract setup executable |
| `-SkipTesseract` | Do not install Tesseract; use whatever is already there |
| `-NoDesktopShortcut` | Start Menu entry only |
| `-Uninstall` | Remove a previous installation |

---

## What the installers actually do

### Java

Both check for **Java 17 or newer** by running `java -version` and reading the
version out of it. If it is missing, the NSIS setup offers to open the download
page, and the PowerShell script warns and carries on so everything else is
already in place. Neither one installs Java.

### Tesseract

The bundled setup is run with its own silent switch, `/S`, and **no `/D`**, so
it installs wherever it wants to by default.

Where that is, is then **looked up rather than assumed**. The payload is a
third-party NSIS installer, and its default directory can differ between builds,
and between per-machine and per-user installs. Both scripts therefore search, in
order:

1. `%ProgramFiles%\Tesseract-OCR`, `%ProgramFiles(x86)%\Tesseract-OCR`,
   `C:\Tesseract-OCR`
2. `%LOCALAPPDATA%\Programs\Tesseract-OCR`, `%LOCALAPPDATA%\Tesseract-OCR`,
   `%APPDATA%\Tesseract-OCR`
3. every uninstall registry entry whose display name starts with `Tesseract`, in
   the 64-bit view, the 32-bit view and the per-user hive — reading
   `InstallLocation`, or the folder of its `UninstallString`

A candidate only counts when it actually contains `tessdata\*.traineddata`.
If Tesseract is already installed, the payload is not run at all.

### Language data

`eng.traineddata` and `ron.traineddata` are bundled and copied into whatever
tessdata folder was found, but **only where a file of that name is missing** —
existing language data is never overwritten.

If no Tesseract installation is found, a `tessdata` folder is created inside the
application directory instead and the bundled files go there. The application
works from that alone, because the OCR engine itself ships inside the jar; only
the language data has to come from somewhere.

### Settings

`invoice-ocr.properties` is written next to the jar with the discovered path:

```properties
ocr.tessdata.path=C:/Program Files/Tesseract-OCR/tessdata
ocr.language=ron+eng
ui.locale=ro
```

`ocr.language` becomes `ron+eng` when Romanian data is present, `eng` otherwise.
Paths are written with **forward slashes** on purpose: a `.properties` file
reads a backslash as an escape character, so `C:\Program Files\...` would be
silently mangled.

An existing settings file is kept as `invoice-ocr.properties.bak`.

### Uninstalling

Both register a Programs and Features entry. Uninstalling removes the
application, the shortcuts and the registry entries, and removes the `tessdata`
folder **only if the installer created it**.

Tesseract is left alone by default, because other software may depend on it.
The NSIS uninstaller asks first and can remove it for you; the PowerShell one
tells you where to find it.

---

## Silent and unattended use

```powershell
invoice-ocr-setup-1.1.1.exe /S                                    # silent install
invoice-ocr-setup-1.1.1.exe /S /D=C:\Apps\InvoiceOCR              # /D must come last, unquoted
"C:\Program Files\Invoice OCR\uninstall.exe" /S                   # silent uninstall
```

In silent mode nothing prompts, including the Java warning and the offer to
remove Tesseract — the details log records what happened instead.

---

## Files here

| File | Purpose |
|---|---|
| `invoice-ocr.nsi` | The NSIS setup script |
| `build-installer.ps1` | Gathers the payload and compiles the setup |
| `Install-InvoiceOCR.ps1` | Standalone installer and uninstaller |
| `assets/invoice-ocr.ico` | Icon for the setup, shortcuts and Programs and Features |
| `payload/` | Created by the build: the Tesseract setup and language files |
