package com.invoiceocr.extraction.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AmountNormalizer")
class AmountNormalizerTest {

    private final AmountNormalizer normalizer = new AmountNormalizer();

    @DisplayName("normalises every separator convention to 1234.56")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'1234,56', '1234.56'",
            "'1234.56', '1234.56'",
            "'1.234,56', '1234.56'",
            "'1,234.56', '1234.56'",
            "'1 234,56', '1234.56'",
            "'12.345.678,90', '12345678.90'",
            "'1234', '1234'",
            "'1.234', '1234'",
            "'0,05', '0.05'"
    })
    void normalisesAmounts(String raw, String expected) {
        assertEquals(expected, normalizer.normalize(raw));
    }

    @DisplayName("returns an empty string for null or blank input")
    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
    void handlesEmptyInput(String raw) {
        assertEquals("", normalizer.normalize(raw));
    }
}
