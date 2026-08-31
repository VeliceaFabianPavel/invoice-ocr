package com.invoiceocr.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.config.ConfigurationBackedOcrSettings;
import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.config.SettingKeys;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.ocr.OcrOptions;
import com.invoiceocr.support.InMemoryConfigurationSource;
import com.invoiceocr.support.SinglePixelImages;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The plan of attempts")
class RecognitionPlanTest {

    private static OcrSettings settings(InMemoryConfigurationSource source) {
        return new ConfigurationBackedOcrSettings(
                source.with(SettingKeys.TESSDATA_PATH, "C:/tessdata"));
    }

    @Test
    @DisplayName("the built-in ladder runs four differently-prepared readings")
    void buildsTheFullLadder() {
        RecognitionPlan plan = RecognitionPlan.forSettings(
                settings(new InMemoryConfigurationSource()));

        assertEquals(4, plan.size());
        assertEquals(List.of("plain", "straightened", "binarised", "sharpened"),
                plan.passes().stream().map(RecognitionPass::name).toList());
    }

    @Test
    @DisplayName("the cheapest pass comes first, so a clean scan costs what it always did")
    void putsTheCheapestPassFirst() {
        RecognitionPlan plan = RecognitionPlan.forSettings(
                settings(new InMemoryConfigurationSource()));

        assertEquals("plain", plan.passes().get(0).name());
        assertTrue(plan.passes().get(0).options().pageSegmentationMode() == OcrOptions.INHERIT,
                "the first pass makes no assumption the configuration has not already made");
    }

    @Test
    @DisplayName("the passes that do assume a layout each assume a different one")
    void variesTheSegmentationMode() {
        RecognitionPlan plan = RecognitionPlan.forSettings(
                settings(new InMemoryConfigurationSource()));

        List<Integer> overridden = plan.passes().stream()
                .map(RecognitionPass::options)
                .filter(OcrOptions::overridesPageSegmentation)
                .map(OcrOptions::pageSegmentationMode)
                .toList();

        assertTrue(overridden.size() >= 2, "at least two passes should try a different layout");
        assertEquals(overridden.size(), overridden.stream().distinct().count(),
                "two passes assuming the same layout would be one pass run twice");
    }

    @Test
    @DisplayName("the ladder is trimmed to the number of passes configured")
    void honoursTheConfiguredLimit() {
        RecognitionPlan plan = RecognitionPlan.forSettings(settings(
                new InMemoryConfigurationSource().with(SettingKeys.MAXIMUM_PASSES, "2")));

        assertEquals(2, plan.size());
    }

    @Test
    @DisplayName("one pass restores the single-pass pipeline of 1.1")
    void supportsASinglePass() {
        RecognitionPlan plan = RecognitionPlan.forSettings(settings(
                new InMemoryConfigurationSource().with(SettingKeys.MAXIMUM_PASSES, "1")));

        assertEquals(1, plan.size());
        assertEquals("plain", plan.passes().get(0).name());
    }

    @Test
    @DisplayName("turning preprocessing off leaves one pass that touches nothing")
    void honoursPreprocessingOff() {
        RecognitionPlan plan = RecognitionPlan.forSettings(settings(
                new InMemoryConfigurationSource().with(SettingKeys.PREPROCESSING_ENABLED, "false")));

        assertEquals(1, plan.size());
        SourceImage image = SinglePixelImages.of("page.png", 4, 4);
        assertEquals(image, plan.passes().get(0).preprocessor().apply(image));
    }

    @Test
    @DisplayName("the passes prepare the image differently, which is the other half")
    void variesThePreparation() {
        RecognitionPlan plan = RecognitionPlan.forSettings(
                settings(new InMemoryConfigurationSource()));

        assertNotEquals(plan.passes().get(0).preprocessor(), plan.passes().get(1).preprocessor());
    }

    @Test
    @DisplayName("a plan with no passes in it is a programming error")
    void refusesAnEmptyPlan() {
        assertThrows(IllegalArgumentException.class, () -> new RecognitionPlan(List.of()));
    }

    @Test
    @DisplayName("a pass has to be named, because the log and the merge both quote it")
    void demandsAName() {
        assertThrows(IllegalArgumentException.class,
                () -> RecognitionPass.of("  ", ImagePreprocessor.identity()));
    }
}
