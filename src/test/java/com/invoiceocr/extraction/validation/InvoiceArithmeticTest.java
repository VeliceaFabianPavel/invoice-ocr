package com.invoiceocr.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Invoice arithmetic")
class InvoiceArithmeticTest {

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("net plus VAT equals total")
    void addsUp() {
        assertTrue(InvoiceArithmetic.addsUp(amount("1000.00"), amount("190.00"), amount("1190.00")));
        assertFalse(InvoiceArithmetic.addsUp(amount("1000.00"), amount("190.00"), amount("1200.00")));
    }

    @Test
    @DisplayName("a rounding difference of a penny is tolerated")
    void toleratesRounding() {
        assertTrue(InvoiceArithmetic.addsUp(amount("1000.00"), amount("190.00"), amount("1190.01")));
    }

    @Test
    @DisplayName("infers the VAT rate the two figures imply")
    void infersTheRate() {
        assertEquals(Optional.of(BigDecimal.valueOf(19)),
                InvoiceArithmetic.impliedRate(amount("1000.00"), amount("190.00")));
        assertEquals(Optional.of(BigDecimal.valueOf(21)),
                InvoiceArithmetic.impliedRate(amount("1000.00"), amount("210.00")));
        assertEquals(Optional.of(BigDecimal.valueOf(9)),
                InvoiceArithmetic.impliedRate(amount("200.00"), amount("18.00")));
    }

    @Test
    @DisplayName("a ratio that is not a real rate implies nothing")
    void refusesAnImpossibleRate() {
        assertEquals(Optional.empty(),
                InvoiceArithmetic.impliedRate(amount("1000.00"), amount("370.00")));
        assertFalse(InvoiceArithmetic.plausiblePair(amount("1000.00"), amount("370.00")));
    }

    @Test
    @DisplayName("a zero net implies no rate rather than dividing by zero")
    void survivesAZeroNet() {
        assertEquals(Optional.empty(), InvoiceArithmetic.impliedRate(BigDecimal.ZERO, amount("19.00")));
    }

    @Test
    @DisplayName("computes the VAT on a net figure")
    void computesVat() {
        assertEquals(amount("190.00"), InvoiceArithmetic.vatOn(amount("1000.00"), BigDecimal.valueOf(19)));
    }

    @Test
    @DisplayName("recovers the net a gross figure contains")
    void recoversNetFromGross() {
        assertEquals(amount("100.00"), InvoiceArithmetic.netOf(amount("119.00"), BigDecimal.valueOf(19)));
        assertEquals(amount("100.00"), InvoiceArithmetic.netOf(amount("121.00"), BigDecimal.valueOf(21)));
    }

    @Test
    @DisplayName("both generations of Romanian rates are recognised")
    void knowsBothRateGenerations() {
        assertTrue(InvoiceArithmetic.VAT_RATES.contains(BigDecimal.valueOf(19)));
        assertTrue(InvoiceArithmetic.VAT_RATES.contains(BigDecimal.valueOf(21)));
        assertTrue(InvoiceArithmetic.VAT_RATES.contains(BigDecimal.valueOf(9)));
        assertTrue(InvoiceArithmetic.VAT_RATES.contains(BigDecimal.valueOf(11)));
    }
}
