package com.invoiceocr.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Per-run OCR overrides")
class OcrOptionsTest {

    @Test
    @DisplayName("inherited options change nothing the configuration decided")
    void inheritsByDefault() {
        OcrOptions options = OcrOptions.inherited();

        assertFalse(options.overridesPageSegmentation());
        assertEquals(6, options.pageSegmentationModeOr(6));
    }

    @Test
    @DisplayName("an override replaces the configured mode")
    void overridesTheMode() {
        OcrOptions options = OcrOptions.pageSegmentation(4);

        assertTrue(options.overridesPageSegmentation());
        assertEquals(4, options.pageSegmentationModeOr(6));
    }

    @Test
    @DisplayName("the inherited instance is shared, since it carries no state")
    void sharesTheInheritedInstance() {
        assertSame(OcrOptions.inherited(), OcrOptions.inherited());
    }

    @Test
    @DisplayName("a negative mode is a mistake, not a sentinel to be smuggled in")
    void refusesANegativeMode() {
        assertThrows(IllegalArgumentException.class, () -> OcrOptions.pageSegmentation(-1));
    }
}
