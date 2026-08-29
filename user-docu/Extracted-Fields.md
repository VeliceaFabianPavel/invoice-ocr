[Home](Home.md) › **Extracted Fields**

# Extracted Fields

The application always looks for the same six fields and always reports all six,
in the same order. A field it cannot find is shown as **N/A** rather than
guessed.

| # | Field (Romanian) | English | Typical value |
|---|---|---|---|
| 1 | Furnizor | Supplier | `SC EXEMPLU DISTRIBUTIE SRL` |
| 2 | Serie / Numar | Series / Number | `FCT-2024/0182` |
| 3 | Data emiterii | Issue date | `05.03.2024` |
| 4 | CUI / CIF | Fiscal code | `RO12345678` |
| 5 | TVA | VAT | `190.00` |
| 6 | Total de plata | Total due | `1190.00` |

---

## How a field is found

The application tries several approaches for each field and keeps the first that
works, from the most reliable to the most resourceful:

1. **The label with the value beside it** — `Furnizor: SC ALFA SRL`.
2. **The label with the value on one of the next lines.** This is what column
   headings and block layouts need:

   ```
   Nr. factura     Data emiterii        FURNIZOR
   GML-7781        02.02.2024           SC ALFA CONSTRUCT SRL
   ```

3. **The value recognised by its own shape**, in the right part of the page — a
   fiscal code is unmistakable wherever it is printed.
4. **A reasoned guess**, when nothing is labelled: the company name in the
   letterhead, the first plausible date, or the largest amount as the total.

So an invoice that labels nothing at all still comes back filled in. When the
page is not an invoice, the guessing stops rather than inventing figures.

## Which labels are recognised

Matching ignores capitals and accepts missing diacritics, so `FURNIZOR`,
`Furnizor` and `furnizor` all work, as do `Vânzător` and `Vanzator`. It also
tolerates the letters recognition confuses with digits, so `RO 33O44SS` is still
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

### 2. Serie / Numar — invoice number

Recognised, in order of preference:

1. **Seria si numarul:** `AB 1024`
2. **Factura fiscala nr.** / **Factura nr.** / **Factura numarul** `FCT-2024/0182`
3. **Nr. factura** / **Numar factura** `0182`
4. **Seria** `AB` **nr** `1024` — series and number printed separately
5. **Invoice no.** / **Invoice number** (English invoices)

Letters, digits, `-` and `/` are kept; the result is upper-cased.

### 3. Data emiterii — issue date

Recognised, in order of preference: **Data facturii**, **Data emiterii**,
**Data**, **Date**.

The value must be a day-first date: `05.03.2024`, `5/3/2024`, `05-03-24`.

### 4. CUI / CIF — fiscal code

Recognised: **Cod unic de identificare fiscala**, **Cod fiscal**, **CUI**,
**CIF** — with or without dots and spaces, so `C.U.I.` and `CUI` both work.

The value is 2 to 10 digits, with an optional `RO` prefix.

### 5. TVA — VAT amount

Recognised, in order of preference: **Total TVA**, **Valoare TVA**, **TVA**.

A rate printed between the label and the amount is skipped, so
`Total TVA 19% 190,00` yields `190.00` — the amount, not the rate.

### 6. Total de plata — total due

Recognised, in order of preference: **Total de plata**, **Total general**,
**Total factura**, **Total**.

A currency written after the amount (`LEI`, `RON`, `EUR`) is ignored.

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
preference order applies to every field above.

---

## How values are cleaned up

What you see in the right-hand panel is normalised, so that two invoices which
print the same amount differently end up identical in your records.

| Field type | Printed on the invoice | Shown by the application |
|---|---|---|
| Amounts | `1.190,00` `1,190.00` `1 190,00` `1190,00 LEI` | `1190.00` |
| Dates | `5.3.2024` `05/03/2024` `05-03-24` | `05.03.2024` |
| Fiscal code | `RO 12.345.678` `ro-12345678` | `RO12345678` |
| Number | ` fct-2024/0182 ` | `FCT-2024/0182` |
| Supplier | `SC   EXEMPLU   SRL |` | `SC EXEMPLU SRL` |

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

Only the six above. In particular it does **not** read individual table rows
(product, quantity, unit price), the delivery address, the bank account or the
registration number `J40/1234/2019`. Those appear in the raw panel, and you can
copy them from there, but they are not parsed into the summary.

---

**See also:** [Preparing Invoices](Preparing-Invoices.md) when fields come out
`N/A` · [Troubleshooting](Troubleshooting.md) for wrong values
