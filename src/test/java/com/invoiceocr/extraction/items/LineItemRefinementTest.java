package com.invoiceocr.extraction.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.ExtractedField;
import com.invoiceocr.domain.FieldConfidence;
import com.invoiceocr.domain.FieldDefinition;
import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.extraction.text.SearchText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The goods table as evidence")
class LineItemRefinementTest {

    private static final String PAGE = """
            Denumire                Cant    Pret      Valoare
            Ciment Portland           10   32,00       320,00
            Nisip spalat sac          25    8,00       200,00

            Total fara TVA                             520,00
            TVA 19%                                     98,80
            Total de plata                             618,80
            """;

    private final LineItemRefinement refinement =
            new LineItemRefinement(new TableLineItemExtractor());

    private InvoiceData refine(String page, ExtractedField... fields) {
        List<ExtractedField> all = new ArrayList<>();
        for (FieldDefinition definition : InvoiceFields.ALL) {
            ExtractedField supplied = null;
            for (ExtractedField field : fields) {
                if (field.definition().equals(definition)) {
                    supplied = field;
                }
            }
            all.add(supplied == null ? ExtractedField.missing(definition) : supplied);
        }
        InvoiceData data = InvoiceData.of(RecognizedText.of(page), all);
        return refinement.refine(data, SearchText.of(data.source()));
    }

    private static ExtractedField amount(FieldDefinition field, String value) {
        return ExtractedField.of(field, value, FieldConfidence.LABELLED, "test");
    }

    @Test
    @DisplayName("attaches the rows it finds")
    void attachesTheRows() {
        assertEquals(2, refine(PAGE).lineItems().size());
    }

    @Test
    @DisplayName("rows that sum to the net amount confirm it")
    void confirmsTheNetAmount() {
        InvoiceData refined = refine(PAGE, amount(InvoiceFields.NET_AMOUNT, "520.00"));

        assertEquals(FieldConfidence.VERIFIED,
                refined.field(InvoiceFields.NET_AMOUNT).orElseThrow().confidence());
    }

    @Test
    @DisplayName("rows that disagree with the net amount leave it exactly as read")
    void leavesADisagreeingNetAlone() {
        InvoiceData refined = refine(PAGE, amount(InvoiceFields.NET_AMOUNT, "999.00"));

        assertEquals(Optional.of("999.00"), refined.valueOf(InvoiceFields.NET_AMOUNT));
        assertEquals(FieldConfidence.LABELLED,
                refined.field(InvoiceFields.NET_AMOUNT).orElseThrow().confidence());
    }

    @Test
    @DisplayName("a missing net amount is supplied by the rows when the VAT agrees")
    void suppliesAMissingNetAmount() {
        InvoiceData refined = refine(PAGE, amount(InvoiceFields.VAT_AMOUNT, "98.80"));

        assertEquals(Optional.of("520.00"), refined.valueOf(InvoiceFields.NET_AMOUNT));
        assertEquals(FieldConfidence.DERIVED,
                refined.field(InvoiceFields.NET_AMOUNT).orElseThrow().confidence());
    }

    @Test
    @DisplayName("with nothing to corroborate them, the rows do not supply a net amount")
    void willNotSupplyAnUncorroboratedNet() {
        assertTrue(refine(PAGE).valueOf(InvoiceFields.NET_AMOUNT).isEmpty(),
                "a table with a row missed by OCR would otherwise become a confident wrong figure");
    }

    @Test
    @DisplayName("a page with no table is returned untouched")
    void leavesATablelessPageAlone() {
        InvoiceData refined = refine("Furnizor: SC ALFA SRL\nTotal de plata 100,00\n");
        assertTrue(refined.lineItems().isEmpty());
    }
}
