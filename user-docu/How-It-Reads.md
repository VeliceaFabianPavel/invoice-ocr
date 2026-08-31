[Home](Home.md) › **How It Reads**

# How It Reads

Version 1.2 changed what happens between clicking **Incarca factura** and seeing
the right-hand panel. This page explains it, because it changes how much you
should trust what comes back — and where to look when something is wrong.

The short version: the application now reads the page **more than once**, and
then **checks** what it read.

---

## It reads the page several times

Recognising text from an image means making two guesses before you have seen
anything: how to prepare the picture, and how the text on it is arranged. Get
either wrong and whole fields go missing — not because the application failed to
understand them, but because they were never transcribed in the first place.

So it tries up to four times, each differently:

| Attempt | What it does to the picture |
|---|---|
| 1 | Enlarges a small scan and converts it to grey — the quickest, and enough for most scans |
| 2 | Straightens a crooked page, then stretches the contrast |
| 3 | The above, plus black-and-white conversion that copes with uneven lighting |
| 4 | Sharpens softened characters and reads the page as one block of text |

**It stops as soon as it is satisfied.** A clean scan is read once and takes
exactly as long as it did before. Only a page that leaves something unread or
uncertain is worth a second look, and only that page pays for one.

Then it compares the answers. If two attempts read the same invoice number, that
agreement counts for a lot — the mistakes recognition makes depend on how the
picture was prepared, so a value that survives two different preparations is very
likely right. A modest reading that two attempts agree on beats a confident
reading that only one produced.

### What this fixes in practice

- **Photographs taken by hand.** A page held at three or four degrees used to
  lose whole lines, because slightly slanted text gets merged with the line
  above it. It is straightened now.
- **A desk lamp on one side.** Half the page in shadow used to come back as a
  black block. Attempt 3 judges each part of the page against its own
  surroundings instead of against one fixed brightness.
- **Faded thermal receipts.** Attempt 2 stretches what little contrast there is
  back over the full range.

---

## It checks what it read

Some values on an invoice can be *verified*, not merely recognised. The
application now does so, and the difference is large: a check does not just
confirm a good reading, it lets a bad one be rejected and the next-best answer
used instead.

### The fiscal code has a control digit

A Romanian CUI is not an arbitrary number — its last digit is computed from the
others. So of two codes printed identically on the same page, the application can
tell which one it read correctly, without knowing anything about where either sat.

### The bank account has a checksum

Every IBAN does. Here the check is a veto: an account that does not verify is
reported as **N/A** rather than shown. An almost-right bank account is worse than
no bank account.

### A date has to exist

`31.02.2024` is a shape, not a day. Previously it would have been printed on the
report as fact; now it is refused and the application looks elsewhere on the
page.

### The amounts have to add up

Every invoice obeys one rule: **net + VAT = total**. Three figures read
independently are three chances to be wrong. Three figures that must add up are
a system that solves itself, and the application uses it four ways:

| What it finds | What it does |
|---|---|
| All three, and they add up | Reports all three as confirmed |
| Two of the three | Works out the third exactly |
| All three, but they do not add up | Recomputes the odd one out from the two that make sense together |
| Only the total, with the rate printed beside it | Recovers the other two |
| Nothing that reconciles | Reports all three as read, and marks them for you to check |

It never invents a figure. Every amount it fills in is implied by two amounts
that were actually printed on the page.

This is where most newly-populated fields come from. An invoice that prints only
"Total fara TVA" and "Total de plata" now yields a VAT figure it never carried.

---

## It tells you what to check

Not every value is equally certain, and the application no longer pretends
otherwise.

The status bar says so first:

```
factura-03.png: 9 campuri identificate, 2 de verificat
```

*(9 fields recognised, 2 to check.)*

And the two are marked in the report with **(?)**:

```
Total de plata      : 1190.00 (?)
```

A mark means the value was worked out or guessed rather than read from its own
label — for instance the largest amount on a page that never labelled a total.
It is usually right. It is simply the one worth a glance at the raw panel before
you use it.

**When nothing is marked, nothing needs checking**, and the status bar uses the
shorter message. That is the point of the mark: it stays meaningful because it is
not always there.

The marks appear in every export that a person reads — PDF, plain text, Markdown
and HTML. The data formats carry the same information as numbers instead: see
[Exporting](Exporting.md).

---

## What it still cannot do

- It cannot read text that is not there. A page too blurred for any of the four
  attempts is a page that needs rescanning — see
  [Preparing Invoices](Preparing-Invoices.md).
- The checks are Romanian. A foreign supplier's fiscal code is reported without
  being confirmed, rather than wrongly rejected.
- The VAT rates it recognises are the ones charged in Romania. An invoice at
  some other rate still has its amounts read; they simply cannot corroborate one
  another.
- Straightening handles a tilt, not a rotation. A page scanned sideways needs
  turning the right way up first.

---

**See also:** [Extracted Fields](Extracted-Fields.md) for what each field means ·
[Settings](Settings.md) to trade speed against accuracy ·
[Preparing Invoices](Preparing-Invoices.md) to give it a better picture
