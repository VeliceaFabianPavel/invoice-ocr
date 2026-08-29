package com.invoiceocr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.InvoiceFields;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.exception.DocumentLoadException;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.extraction.RuleBasedInvoiceParser;
import com.invoiceocr.extraction.rules.RomanianInvoiceRuleProvider;
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.ocr.OcrEngine;
import com.invoiceocr.ocr.OcrEngineFactory;
import com.invoiceocr.support.FixedTextOcrEngine;
import com.invoiceocr.support.SinglePixelImages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultInvoiceRecognitionService")
class DefaultInvoiceRecognitionServiceTest {

    private static final Path DOCUMENT = Path.of("invoice.png");

    private final InvoiceParser parser = new RuleBasedInvoiceParser(new RomanianInvoiceRuleProvider());

    @Test
    @DisplayName("runs load, preprocess, recognise and parse in that order")
    void runsThePipelineInOrder() {
        List<String> calls = new ArrayList<>();
        ImagePreprocessor preprocessor = image -> {
            calls.add("preprocess");
            return image;
        };
        OcrEngineFactory engineFactory = () -> {
            calls.add("createEngine");
            return new FixedTextOcrEngine("Furnizor: ACME SRL");
        };

        InvoiceData data = new DefaultInvoiceRecognitionService(
                recordingLoader(calls), preprocessor, engineFactory, parser).recognize(DOCUMENT);

        assertEquals(List.of("load", "preprocess", "createEngine"), calls);
        assertEquals(Optional.of("ACME SRL"), data.valueOf(InvoiceFields.SUPPLIER));
    }

    @Test
    @DisplayName("hands the preprocessed image, not the original, to the engine")
    void recognisesThePreprocessedImage() {
        SourceImage preprocessed = SinglePixelImages.of("invoice.png", 4, 4);
        AtomicReference<SourceImage> seenByEngine = new AtomicReference<>();

        new DefaultInvoiceRecognitionService(
                fixedLoader(),
                image -> preprocessed,
                () -> new RecordingEngine(seenByEngine),
                parser).recognize(DOCUMENT);

        assertSame(preprocessed, seenByEngine.get());
    }

    @Test
    @DisplayName("closes the engine even when recognition throws")
    void closesTheEngineOnFailure() {
        FailingEngine engine = new FailingEngine();

        assertThrows(IllegalStateException.class,
                () -> new DefaultInvoiceRecognitionService(
                        fixedLoader(), ImagePreprocessor.identity(), () -> engine, parser).recognize(DOCUMENT));

        assertTrue(engine.isClosed());
    }

    @Test
    @DisplayName("releases the engine after a successful run as well")
    void closesTheEngineOnSuccess() {
        FixedTextOcrEngine engine = new FixedTextOcrEngine("Total de plata 100,00");

        new DefaultInvoiceRecognitionService(
                fixedLoader(), ImagePreprocessor.identity(), () -> engine, parser).recognize(DOCUMENT);

        assertEquals(1, engine.closeCount());
    }

    @Test
    @DisplayName("lets a load failure travel to the caller untouched")
    void propagatesLoadFailures() {
        DocumentLoader failing = new DocumentLoader() {
            @Override
            public SourceImage load(Path path) {
                throw new DocumentLoadException("cannot read " + path);
            }

            @Override
            public boolean supports(Path path) {
                return true;
            }
        };

        DocumentLoadException failure = assertThrows(DocumentLoadException.class,
                () -> new DefaultInvoiceRecognitionService(
                        failing, ImagePreprocessor.identity(),
                        () -> new FixedTextOcrEngine("unused"), parser).recognize(DOCUMENT));

        assertEquals("cannot read invoice.png", failure.getMessage());
    }

    private static DocumentLoader fixedLoader() {
        return recordingLoader(new ArrayList<>());
    }

    private static DocumentLoader recordingLoader(List<String> calls) {
        return new DocumentLoader() {
            @Override
            public SourceImage load(Path path) {
                calls.add("load");
                return SinglePixelImages.of(path.toString());
            }

            @Override
            public boolean supports(Path path) {
                return true;
            }
        };
    }

    /** Captures the image it is asked to recognise. */
    private static final class RecordingEngine implements OcrEngine {

        private final AtomicReference<SourceImage> seen;

        private RecordingEngine(AtomicReference<SourceImage> seen) {
            this.seen = seen;
        }

        @Override
        public RecognizedText recognize(SourceImage image) {
            seen.set(image);
            return RecognizedText.of("Total de plata 100,00");
        }
    }

    /** Fails on recognition and records whether it was closed afterwards. */
    private static final class FailingEngine implements OcrEngine {

        private boolean closed;

        @Override
        public RecognizedText recognize(SourceImage image) {
            throw new IllegalStateException("engine exploded");
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
