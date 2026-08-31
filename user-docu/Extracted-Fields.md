[Home](Home.md) › **Extracted Fields**

# Extracted Fields

The application always looks for the same twelve fields and always reports all
twelve, in the same order. A field it cannot find is shown as **N/A** rather
than guessed.

| # | Field (Romanian) | English | Typical value |
|---|---|---|---|
| 1 | Furnizor | Supplier | `SC EXEMPLU DISTRIBUTIE SRL` |
| 2 | Cumparator | Buyer | `SC BETA COMERT SRL` |
| 3 | Serie / Numar | Series / Number | `FCT-2024/0182` |
| 4 | Data emiterii | Issue date | `05.03.2024` |
| 5 | Data scadentei | Due date | `04.04.2024` |
| 6 | CUI / CIF | Fiscal code | `RO12345674` |
| 7 | Reg. Comertului | Trade register | `J40/1122/2015` |
| 8 | Cont bancar (IBAN) | Bank account | `RO49 AAAA 1B31 0075 9384 0000` |
| 9 | Total fara TVA | Net amount | `1000.00` |
| 10 | TVA | VAT | `190.00` |
| 11 | Total de plata | Total due | `1190.00` |
| 12 | Moneda | Currency | `RON` |

Six of these — buyer, due date, trade register, IBAN, net amount and currency —
are new in version 1.2. Underneath them, the rows of the goods table are read
too: see [Line Items](Line-Items.md).

---

## How a field is found

The application tries several approaches for each field, from the most reliable
to the most resourceful:

1. **The label with the value beside it** — `Furnizor: SC ALFA SRL`.
2. **The label with the value on one of the next lines.** This is what column
   headings and block layouts need:

   ```
   Nr. factura     Data emiterii        Termen de plata
   GML-7781        02.02.2024           02.03.2024
   ```

   Notice the second and third columns. The application takes the value under
   **its own heading**, not simply the first one it meets, so `Termen de plata`
   reads `02.03.2024` and not the issue date beside it.

3. **The value recognised by its own shape**, in the right part of the page — a
   fiscal code is unmistakable wherever it is printed.
4. **A reasoned guess**, when nothing is labelled: the company name in the
   letterhead, the first plausible date, or the largest amount as the total.

So an invoice that labels nothing at all still comes back filled in. When the
page is not an invoice, the guessing stops rather than inventing figures.

**And then it checks.** Several of these fields can be verified — a fiscal code
by its control digit, a bank account by its checksum, a date against the
calendar, the three amounts against each other. Where a check fails, the
application goes back and tries the next approach instead of reporting what it
first found. [How It Reads](How-It-Reads.md) explains this in full.

## Which labels are recognised

Matching ignores capitals and accepts missing diacritics, so `FURNIZOR`,
`Furnizor` and `furnizor` all work, as do `Vânzător` and `Vanzator`. It also
tolerates the letters recognition confuses with digits, so `RO 33O44S4` is still
read as a fiscal code and `l.428,OO` as an amount.

### 1. Furnizor — supplier

Recognised labels: **Furnizor**, **Emitent**, **Vânzător**, **Prestator**

Takes the rest of the line after the label, or the company name on one of the
next lines when the label is a heading. A supplier with **no label at all** is
still found: the application looks for a line carrying a legal form — `SRL`,
`SA`, `PFA`, `SNC` — and takes that, skipping address lines that happen to
mention one.

On an invoice that names the buyer as well, the search is confined to the
supplier's part of the page, so the buyer's name and code are never reported in
the supplier's fields — even when the buyer is printed first.

### 2. Cumparator — buyer

Recognised labels: **Cumparator**, **Client**, **Beneficiar**, **Achizitor**

The mirror image of the supplier, with one deliberate difference: there is no
letterhead fallback. An invoice that never names a buyer reports **N/A**, rather
than repeating the supplier under a second heading.

### 3. Serie / Numar — invoice number

Recognised, in order of preference:

1. **Seria si numarul:** `AB 1024`
2. **Factura fiscala nr.** / **Factura nr.** / **Factura numarul** `FCT-2024/0182`
3. **Nr. factura** / **Numar factura** `0182`
4. **Seria** `AB` **nr** `1024` — series and number printed separately
5. **Invoice no.** / **Invoice number** (English invoices)

Letters, digits, `-` and `/` are kept; the result is upper-cased.

### 4. Data emiterii — issue date

Recognised, in order of preference: **Data facturii**, **Data emiterii**,
**Data**, **Date**.

The value must be a day-first date: `05.03.2024`, `5/3/2024`, `05-03-24` — and
it must be a day that exists. `31.02.2024` is a shape, not a date, so the
application refuses it and looks elsewhere on the page.

### 5. Data scadentei — due date

Recognised labels: **Termen de plata**, **Scadenta**, **Data scadentei**.

Most invoices never print this date. What they print instead is the *term* —
"Termen de plata: 30 zile" — which says the same thing a different way, so the
application adds the days to the issue date and reports the result. A due date
earlier than the issue date is marked for you to check rather than believed.

### 6. CUI / CIF — fiscal code

Recognised: **Cod unic de identificare fiscala**, **Cod fiscal**, **CUI**,
**CIF** — with or without dots and spaces, so `C.U.I.` and `CUI` both work.

The value is 2 to 10 digits, with an optional `RO` prefix. Romanian fiscal codes
carry a **control digit**, so the application can tell whether it read one
correctly. Of two codes printed identically on the same page, the one that adds
up is the one it reports.

### 7. Reg. Comertului — trade register number

Recognised: **Reg. Com.**, **Registrul Comertului**, **Nr. ORC** — and the shape
`J40/1122/2015` on its own, which is distinctive enough to be found without a
label.

### 8. Cont bancar — IBAN

Recognised: **IBAN**, **Cont**, **Cont bancar**, **Cod IBAN**.

Every IBAN carries a checksum, and this field is the one where a plausible wrong
answer costs real money. So the application applies the checksum as a **veto**:
if an account does not verify, it is reported as **N/A** rather than shown. What
you see in this field has been checked arithmetically.

The value is printed in groups of four, which is how a bank account is meant to
be read back.

### 9. Total fara TVA — net amount

Recognised: **Total fara TVA**, **Valoare fara TVA**, **Baza impozabila**,
**Subtotal**.

Frequently not printed at all — in which case it is worked out, either from the
other two amounts or by adding up the rows of the goods table. See
[How It Reads](How-It-Reads.md).

### 10. TVA — VAT amount

Recognised, in order of preference: **Total TVA**, **Valoare TVA**, **TVA**.

A rate printed between the label and the amount is skipped, so
`Total TVA 19% 190,00` yields `190.00` — the amount, not the rate. `Total fara
TVA` is the *net* line and is never mistaken for this one.

### 11. Total de plata — total due

Recognised, in order of preference: **Total de plata**, **Total general**,
**Total factura**, **Total**.

A currency written after the amount is not swallowed into it; it goes into the
currency field instead.

### 12. Moneda — currency

Recognised: `RON`, `LEI`, `EUR`, `USD`, `GBP`, `CHF`, `MDL`, read from the
totals block so that a currency word elsewhere on the page cannot be mistaken
for it. `LEI` is reported as **RON** — the same currency under its official
code, which is what an accounting import expects.

---

## Why the order matters

Invoices routinely print a subtotal labelled `Total` above the real
`Total de plata`:

```
Total                 1.000,00
Total TVA 19%           190,00
Total de plata        1.190,00
```

The application checks the **most specific label first**, so it reports
`1190.00`, not the `1.000,00` that appears higher on the page. The same
preference order applies to every field above — and where a value can be
checked, a later approach whose answer *verifies* is preferred to an earlier one
whose answer does not.

---

## How values are cleaned up

What you see in the right-hand panel is normalised, so that two invoices which
print the same amount differently end up identical in your records.

| Field type | Printed on the invoice | Shown by the application |
|---|---|---|
| Amounts | `1.190,00` `1,190.00` `1 190,00` `1190,00 LEI` | `1190.00` |
| Dates | `5.3.2024` `05/03/2024` `05-03-24` | `05.03.2024` |
| Fiscal code | `RO 12.345.674` `ro-12345674` | `RO12345674` |
| Register number | `J 40 / 1122 / 2015` | `J40/1122/2015` |
| IBAN | `ro49aaaa1b3100759384 0000` | `RO49 AAAA 1B31 0075 9384 0000` |
| Currency | `lei` | `RON` |
| Number | ` fct-2024/0182 ` | `FCT-2024/0182` |
| Supplier | `SC   EXEMPLU   SRL \|` | `SC EXEMPLU SRL` |

**Amounts** always use a dot for decimals and no thousands separator, which is
what spreadsheets and accounting imports expect. The rule is positional: the last
separator is treated as a decimal point only when exactly two digits follow it.

**Dates** always come out as `dd.MM.yyyy`, and a two-digit year becomes 20xx
(`24` → `2024`). If a value is not a plausible date — `32.01.2024`, or an
ISO-style `2024-03-05` — it is shown exactly as it was read rather than
reformatted into something wrong. That is a deliberate signal to check the raw
panel.

---

## Fields the application does not extract

Only the twelve above, plus the goods table. In particular it does **not** read
the delivery address, the person who signed, the means of transport or the
payment reference. Those appear in the raw panel, and you can copy them from
there, but they are not parsed into the summary.

---

**See also:** [Line Items](Line-Items.md) for the goods table ·
[How It Reads](How-It-Reads.md) for the checks behind these values ·
[Preparing Invoices](Preparing-Invoices.md) when fields come out `N/A` ·
[Troubleshooting](Troubleshooting.md) for wrong values
