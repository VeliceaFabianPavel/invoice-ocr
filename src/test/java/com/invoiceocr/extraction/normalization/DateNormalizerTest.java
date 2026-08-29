package com.invoiceocr.extraction.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("DateNormalizer")
class DateNormalizerTest {

    private final DateNormalizer normalizer = new DateNormalizer();

    @DisplayName("pads the parts and unifies the separator")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'5.3.2024', '05.03.2024'",
            "'05/03/2024', '05.03.2024'",
            "'05-03-24', '05.03.2024'",
            "'31.12.2023', '31.12.2023'"
    })
    void normalisesDates(String raw, String expected) {
        assertEquals(expected, normalizer.normalize(raw));
    }

    @DisplayName("leaves implausible or unparsable values untouched, rather than inventing a date")
    @ParameterizedTest
    @CsvSource({
            "'32.01.2024', '32.01.2024'",
            "'05.13.2024', '05.13.2024'",
            "'2024-03-05', '2024-03-05'",
            "'not a date', 'not a date'"
    })
    void keepsSuspiciousValues(String raw, String expected) {
        assertEquals(expected, normalizer.normalize(raw));
    }
}
