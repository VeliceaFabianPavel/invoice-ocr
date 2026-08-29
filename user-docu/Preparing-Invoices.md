[Home](Home.md) › **Preparing Invoices**

# Preparing Invoices

Recognition quality is decided before the application ever sees the file. A
clean scan gives 6 fields out of 6; a crooked phone photo in a dim office gives
two, and no setting will rescue it.

This page is the highest-value one in this guide when results disappoint.

---

## Accepted file formats

| Format | Extensions | Notes |
|---|---|---|
| PNG | `.png` | **Best choice.** Lossless, sharp edges |
| TIFF | `.tif`, `.tiff` | Excellent; what most scanners produce |
| JPEG | `.jpg`, `.jpeg` | Fine at high quality; heavy compression blurs characters |
| BMP | `.bmp` | Works, but large files |
| GIF | `.gif` | Works; only suitable for simple black-and-white scans |

Files with any other extension are not offered in the dialog. The list is
configurable — see [Settings](Settings.md#document-formats).

### PDF invoices

The application does not open PDFs. Convert the page to an image first:

- open the PDF in your reader and **export or print to PNG/TIFF at 300 dpi**, or
- scan the printed page directly.

Avoid screenshotting a PDF at screen size — that produces roughly 96 dpi, well
below what recognition needs.

---

## Scanning: the settings that matter

| Setting | Recommended | Why |
|---|---|---|
| Resolution | **300 dpi** | The single most important setting. 150 dpi loses thin digits; above 600 dpi only costs time |
| Colour mode | Greyscale or black-and-white | Colour adds nothing; the application converts to grey anyway |
| Format | PNG or TIFF | Avoid re-compressing a JPEG repeatedly |
| Straightness | As straight as you can | Tilted text is the second most common cause of poor results |
| Coverage | The whole page, edge to edge | A cut-off footer means a missing total |

---

## Photographing an invoice

Sometimes a phone is all you have. In that case:

- Lay the invoice **flat** on a dark, non-reflective surface.
- Shoot **straight down**, not at an angle — perspective distortion badly hurts
  recognition.
- Use **even, indirect light**. Avoid your own shadow and glossy reflections.
- Fill the frame with the page; do not include the desk.
- Check the photo is **sharp** before loading it. If the text is blurry on your
  phone, it is unreadable to the application.
- Prefer your phone's **document scan** mode if it has one — it deskews and
  flattens automatically.

---

## What the application does for you

Before recognition, two automatic corrections are applied:

1. **Enlargement** — images narrower than 1000 pixels are scaled up (at most
   4×), because characters below a certain size cannot be recognised at all.
2. **Greyscale conversion** — colour is removed, which makes the text/background
   separation more reliable.

Both are on by default and can be turned off in
[Settings](Settings.md#image-preparation), though there is rarely a reason to.

Note what this does **not** include: the application does not straighten a
tilted page, remove shadows or sharpen a blurred photo. Those must be right
before you load the file.

---

## Quick checklist

Before blaming the application, confirm:

- [ ] The image is at least ~1000 pixels wide (a 300 dpi A4 scan is ~2480).
- [ ] Text is sharp when you zoom to 100 % in an image viewer.
- [ ] The page is straight, not rotated or upside down.
- [ ] The whole invoice is visible, including the totals block.
- [ ] There are no large shadows or glare spots across the text.
- [ ] The recognition language matches the invoice —
      [`ocr.language`](Settings.md#recognition-language).

If all six hold and results are still poor, the left-hand raw panel will show
you what the engine actually saw, and
[Troubleshooting](Troubleshooting.md#poor-or-wrong-results) covers what to change
next.

---

**See also:** [Settings](Settings.md) · [Troubleshooting](Troubleshooting.md)
