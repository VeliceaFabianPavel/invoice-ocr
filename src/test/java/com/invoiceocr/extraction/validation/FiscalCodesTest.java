package com.invoiceocr.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Romanian fiscal codes")
class FiscalCodesTest {

    @DisplayName("accepts a code whose control digit adds up")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"12345674", "9876544", "445564", "778895", "2468106", "221109"})
    void acceptsValidCodes(String code) {
        assertTrue(FiscalCodes.isValid(code));
    }

    @DisplayName("rejects the same code with any other control digit")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"12345670", "12345675", "9876543", "445566", "778899"})
    void rejectsWrongControlDigits(String code) {
        assertFalse(FiscalCodes.isValid(code));
    }

    @Test
    @DisplayName("the country prefix is ignored, however OCR read its letter O")
    void ignoresCountryPrefix() {
        assertTrue(FiscalCodes.isValid("RO12345674"));
        assertTrue(FiscalCodes.isValid("R012345674"));
        assertTrue(FiscalCodes.isValid("RO 12.345.674"));
    }

    @Test
    @DisplayName("computes the digit a body should be followed by")
    void computesTheControlDigit() {
        assertEquals(4, FiscalCodes.controlDigitFor("1234567"));
        assertEquals(9, FiscalCodes.controlDigitFor("22110"));
    }

    @Test
    @DisplayName("repairs OCR letters and confirms the reading against the checksum")
    void repairsAndVerifies() {
        assertEquals(Optional.of("RO3304454"), FiscalCodes.repair("RO 33O44S4"));
    }

    @Test
    @DisplayName("declines to repair a reading that still does not add up")
    void declinesWhenNothingValidates() {
        assertEquals(Optional.empty(), FiscalCodes.repair("RO 33O44SS"));
    }

    @Test
    @DisplayName("a valid code is proven, a wrong one doubted, a short one left alone")
    void checkGradesTheThreeCases() {
        assertEquals(Verdict.PROVEN, FiscalCodes.check().check("RO12345674"));
        assertEquals(Verdict.DOUBTFUL, FiscalCodes.check().check("RO12345678"));
        assertEquals(Verdict.UNPROVEN, FiscalCodes.check().check("RO12"),
                "codes too short to carry a meaningful checksum are not rejected");
    }

    @Test
    @DisplayName("a code longer than the format allows is not valid")
    void rejectsOverlongCodes() {
        assertFalse(FiscalCodes.isValid("12345678901234"));
    }
}
