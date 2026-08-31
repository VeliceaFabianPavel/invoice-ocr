[Home](Home.md) › **Line Items**

# Line Items

New in version 1.2: as well as the twelve header fields, the application reads
the **rows of the goods table** — what was sold, how much of it, at what price.

Until now this was the most-requested thing it could not do. The rows appeared in
the raw panel and had to be copied out by hand.

---

## What you get

For a table printed like this:

```
Nr. crt  Denumire produs        Cant     Pret      Valoare
1        Ciment Portland          10    32,00       320,00
2        Nisip spalat sac         25     8,00       200,00
3        Caramida BCA            100     4,50       450,00
4        Transport                 1    30,00        30,00
```

the report shows:

```
----------------------------------------------
Articole
----------------------------------------------
Denumire                  Cant  Pret unitar     Valoare
Ciment Portland             10        32.00      320.00
Nisip spalat sac            25         8.00      200.00
Caramida BCA               100         4.50      450.00
Transport                    1        30.00       30.00
```

Every export carries the same rows. See [Exporting](Exporting.md).

---

## What it copes with

**Any combination of columns.** Invoices print different ones. The application
reads each row **from the right**, where the meaning is stable: the last number
on a line is what the row comes to, and everything before the first number is
what was sold. A row that lost its unit-price column to a smudge still gives you
a description and a value.

**A "Nr. crt" column.** The row number in front of the description is recognised
as a row number and dropped, rather than being read as the quantity.

**Two-column tables.** With only two figures on a row, the application decides
by the decimals: `2  450,00` is a quantity and a value; `30,50  450,00` is a unit
price and a value.

**Rules and separators.** Lines of dashes, blank lines and a subtotal printed in
the middle of the table are not rows and are skipped.

Columns the invoice did not print are shown as **N/A**, not as zero.

---

## When it finds nothing

The table has to have a **heading row** — a line carrying both a description
column (`Denumire`, `Descriere`, `Produs`, `Articol`, `Nr. crt`) and a figure
column (`Cant`, `Pret`, `Valoare`, `Suma`, `U.M.`).

Without one, the application reports no rows at all. That is deliberate: an
invoice whose table cannot be located is one whose rows should not be guessed
at, and a made-up table is worse than none.

If your invoices use headings the application does not know, the rows will be
missing while every header field is read normally. The raw panel still shows
them.

---

## The rows check the total

There is a second reason to read the table, beyond copying it out: the rows have
to **add up to the net amount**. That gives the application a second, independent
reading of a figure it otherwise takes on trust.

- If the net amount was printed and the rows agree with it, both are confirmed.
- If the net amount was **not** printed, the sum of the rows can supply it — but
  only when another figure on the page agrees. A table with a row that
  recognition missed sums to too little, and a confidently wrong net amount is
  worse than an absent one.

You will see the result of this in the report rather than the mechanism: an
invoice whose "Total fara TVA" line was never printed now usually has one.

---

## Turning it off

If you only want the header fields, add this to `invoice-ocr.properties` next to
the application:

```properties
extraction.lineItems.enabled=false
```

Or keep reading the table but leave it out of reports:

```properties
report.lineItems=false
```

See [Settings](Settings.md).

---

**See also:** [Extracted Fields](Extracted-Fields.md) for the header fields ·
[Exporting](Exporting.md) for how the rows appear in each format ·
[How It Reads](How-It-Reads.md) for the checking the rows take part in
