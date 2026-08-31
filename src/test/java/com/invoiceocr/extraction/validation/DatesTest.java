package com.invoiceocr.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Calendar validation")
class DatesTest {

    @Test
    @DisplayName("parses the canonical form the normalisers produce")
    void parsesCanonicalDates() {
        assertEquals(Optional.of(LocalDate.of(2024, 3, 5)), Dates.parse("05.03.2024"));
    }

    @DisplayName("rejects a day that does not exist")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"31.02.2024", "30.02.2024", "32.01.2024", "01.13.2024", "29.02.2023"})
    void rejectsImpossibleDays(String date) {
        assertTrue(Dates.parse(date).isEmpty());
        assertEquals(Verdict.DOUBTFUL, Dates.check().check(date));
    }

    @Test
    @DisplayName("a leap day in a leap year is a real day")
    void acceptsALeapDay() {
        assertEquals(Optional.of(LocalDate.of(2024, 2, 29)), Dates.parse("29.02.2024"));
    }

    @Test
    @DisplayName("a year far outside the plausible range is not an invoice date")
    void rejectsAbsurdYears() {
        assertTrue(Dates.parse("01.01.1900").isEmpty());
        assertTrue(Dates.parse("01.01.2500").isEmpty());
    }

    @Test
    @DisplayName("text the normaliser could not canonicalise is passed through unproven")
    void passesThroughUnrecognisedText() {
        assertEquals(Verdict.UNPROVEN, Dates.check().check("scadent"));
        assertEquals(Verdict.UNPROVEN, Dates.check().check("5.3.24"));
    }

    @Test
    @DisplayName("renders a date back into the form the rest of the application uses")
    void formatsBack() {
        assertEquals("05.03.2024", Dates.format(LocalDate.of(2024, 3, 5)));
    }
}
