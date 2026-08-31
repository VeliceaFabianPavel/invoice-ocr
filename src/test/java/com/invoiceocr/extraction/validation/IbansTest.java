package com.invoiceocr.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IBAN validation")
class IbansTest {

    private static final String VALID = "RO49 AAAA 1B31 0075 9384 0000";

    @Test
    @DisplayName("accepts an account that passes mod-97, spaced or not")
    void acceptsAValidAccount() {
        assertTrue(Ibans.isValid(VALID));
        assertTrue(Ibans.isValid("RO49AAAA1B310075938400 00"));
        assertTrue(Ibans.isValid("ro49aaaa1b3100759384 0000"));
    }

    @Test
    @DisplayName("one wrong character fails the checksum")
    void rejectsASingleTypo() {
        assertFalse(Ibans.isValid("RO49 AAAA 1B31 0075 9384 0001"));
        assertFalse(Ibans.isValid("RO48 AAAA 1B31 0075 9384 0000"));
    }

    @Test
    @DisplayName("a Romanian account of the wrong length is refused before the checksum")
    void enforcesRomanianLength() {
        assertFalse(Ibans.isValid("RO49 AAAA 1B31 0075 9384"));
    }

    @Test
    @DisplayName("refuses text that is not an account at all")
    void rejectsNonAccounts() {
        assertFalse(Ibans.isValid(""));
        assertFalse(Ibans.isValid("1234567890123456"));
        assertFalse(Ibans.isValid("Total de plata 1.190,00"));
    }

    @Test
    @DisplayName("formats in groups of four, which is how it gets copied by eye")
    void formatsInGroups() {
        assertEquals(VALID, Ibans.format("RO49AAAA1B3100759384 0000"));
    }

    @Test
    @DisplayName("the check is a veto: an account either verifies or is not an account")
    void checkIsAVeto() {
        assertEquals(Verdict.PROVEN, Ibans.check().check(VALID));
        assertEquals(Verdict.IMPOSSIBLE, Ibans.check().check("RO49 AAAA 1B31 0075 9384 0001"));
    }
}
