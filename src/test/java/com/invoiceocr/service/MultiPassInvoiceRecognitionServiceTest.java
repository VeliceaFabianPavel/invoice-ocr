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
import com.invoiceocr.extraction.validation.ArithmeticRefinement;
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.ocr.OcrEngine;
import com.invoiceocr.ocr.OcrEngineFactory;
import com.invoiceocr.ocr.OcrOptions;
import com.invoiceocr.recognition.InvoiceDataMerger;
import com.invoiceocr.recognition.RecognitionPass;
import com.invoiceocr.recognition.RecognitionPlan;
import com.invoiceocr.support.FixedTextOcrEngine;
import com.invoiceocr.support.SinglePixelImages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reading a page more than once")
class MultiPassInvoiceRecognitionServiceTest {

    private static final Path DOCUMENT = Path.of("invoice.png");

    /** A clean invoice: everything labelled, everything adding up. */
    private static final String CLEAN = """
            Furnizor: SC ALFA SRL
            CUI: RO 12345674
            Factura nr. ALF-100 din 05.03.2024
            Total fara TVA                            1.000,00
            TVA 19%                                     190,00
            Total de plata                            1.190,00
            """;

    /** The same page, read badly: the fiscal code fails its checksum. */
    private static final String DAMAGED = """
            Furnizor: SC ALFA SRL
            CUI: RO 12345679
            Factura nr. ALF-1OO din 05.03.2024
            Total de plata                            1.190,00
            """;

    private final InvoiceParser parser = new RuleBasedInvoiceParser(
            new RomanianInvoiceRuleProvider(), List.of(new ArithmeticRefinement()));

    private static DocumentLoader loader() {
        return recordingLoader(new ArrayList<>());
    }

    /** A loader that decodes nothing and notes every call, so passes can be counted. */
    private static DocumentLoader recordingLoader(List<Path> loads) {
        return new DocumentLoader() {
            @Override
            public SourceImage load(Path path) {
                loads.add(path);
                return SinglePixelImages.of(path.toString());
            }

            @Override
            public boolean supports(Path path) {
                return true;
            }
        };
    }

    /** Hands each pass the next transcription in the list, and records what ran. */
    private static final class ScriptedEngines implements OcrEngineFactory {

        private final List<String> transcriptions;
        private final List<Integer> segmentationModes = new ArrayList<>();
        private int next;

        ScriptedEngines(List<String> transcriptions) {
            this.transcriptions = transcriptions;
        }

        @Override
        public OcrEngine create(OcrOptions options) {
            segmentationModes.add(options.pageSegmentationMode());
            String text = transcriptions.get(Math.min(next, transcriptions.size() - 1));
            next++;
            return new OcrEngine() {
                @Override
                public RecognizedText recognize(SourceImage image) {
                    return RecognizedText.of(text);
                }
            };
        }

        int passesRun() {
            return next;
        }
    }

    private InvoiceData recognize(ScriptedEngines engines, RecognitionPlan plan, double target) {
        return new MultiPassInvoiceRecognitionService(
                loader(), engines, parser, plan, new InvoiceDataMerger(), target).recognize(DOCUMENT);
    }

    private static RecognitionPlan planOf(int passes) {
        List<RecognitionPass> all = new ArrayList<>();
        for (int i = 0; i < passes; i++) {
            all.add(RecognitionPass.of("pass-" + i, ImagePreprocessor.identity(), i + 3));
        }
        return new RecognitionPlan(all);
    }

    @Test
    @DisplayName("a clean page is read once and the rest of the ladder is not paid for")
    void stopsEarlyOnACleanPage() {
        ScriptedEngines engines = new ScriptedEngines(List.of(CLEAN, DAMAGED, DAMAGED, DAMAGED));

        InvoiceData data = recognize(engines, planOf(4), 0.80);

        assertEquals(1, engines.passesRun(), "nothing was left for a second reading to improve");
        assertEquals(Optional.of("RO12345674"), data.valueOf(InvoiceFields.FISCAL_CODE));
    }

    @Test
    @DisplayName("a poor first reading is followed by the rest of the ladder")
    void continuesWhenTheFirstReadingIsPoor() {
        ScriptedEngines engines = new ScriptedEngines(List.of(DAMAGED, CLEAN, CLEAN, CLEAN));

        recognize(engines, planOf(4), 0.95);

        assertTrue(engines.passesRun() > 1, "the first reading left fields flagged for review");
    }

    @Test
    @DisplayName("a later pass rescues what the first one got wrong")
    void aLaterPassRescuesTheField() {
        ScriptedEngines engines = new ScriptedEngines(List.of(DAMAGED, CLEAN));

        InvoiceData data = recognize(engines, planOf(2), 0.99);

        assertEquals(Optional.of("RO12345674"), data.valueOf(InvoiceFields.FISCAL_CODE),
                "the reading whose control digit adds up wins");
        assertEquals(Optional.of("190.00"), data.valueOf(InvoiceFields.VAT_AMOUNT),
                "and the fields only the good pass could see come with it");
    }

    @Test
    @DisplayName("each pass tells the engine what it assumes about the layout")
    void passesTheSegmentationModeThrough() {
        ScriptedEngines engines = new ScriptedEngines(List.of(DAMAGED, DAMAGED, DAMAGED));

        recognize(engines, planOf(3), 0.99);

        assertEquals(List.of(3, 4, 5), engines.segmentationModes);
    }

    @Test
    @DisplayName("the document is decoded once however many passes run")
    void decodesTheImageOnce() {
        List<Path> loads = new ArrayList<>();
        DocumentLoader counting = recordingLoader(loads);
        ScriptedEngines engines = new ScriptedEngines(List.of(DAMAGED, DAMAGED, DAMAGED, DAMAGED));

        new MultiPassInvoiceRecognitionService(counting, engines, parser, planOf(4),
                new InvoiceDataMerger(), 0.99).recognize(DOCUMENT);

        assertEquals(4, engines.passesRun());
        assertEquals(1, loads.size(), "a second reading costs one OCR call, not a second decode");
    }

    @Test
    @DisplayName("a plan of one pass behaves exactly as the single-pass pipeline did")
    void honoursASinglePassPlan() {
        ScriptedEngines engines = new ScriptedEngines(List.of(CLEAN, CLEAN));

        recognize(engines, RecognitionPlan.singlePass(ImagePreprocessor.identity()), 0.99);

        assertEquals(1, engines.passesRun());
    }

    // ------------------------------------------------------- the pipeline

    @Test
    @DisplayName("runs load, preprocess, recognise and parse in that order")
    void runsThePipelineInOrder() {
        List<String> calls = new ArrayList<>();
        DocumentLoader loader = new DocumentLoader() {
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
        RecognitionPlan plan = new RecognitionPlan(List.of(
                RecognitionPass.of("only", image -> {
                    calls.add("preprocess");
                    return image;
                })));
        OcrEngineFactory engines = options -> {
            calls.add("createEngine");
            return new FixedTextOcrEngine(CLEAN);
        };

        InvoiceData data = new MultiPassInvoiceRecognitionService(
                loader, engines, parser, plan, new InvoiceDataMerger(), 0.80).recognize(DOCUMENT);

        assertEquals(List.of("load", "preprocess", "createEngine"), calls);
        assertEquals(Optional.of("SC ALFA SRL"), data.valueOf(InvoiceFields.SUPPLIER));
    }

    @Test
    @DisplayName("hands the preprocessed image, not the original, to the engine")
    void recognisesThePreprocessedImage() {
        SourceImage preprocessed = SinglePixelImages.of("invoice.png", 4, 4);
        AtomicReference<SourceImage> seenByEngine = new AtomicReference<>();

        new MultiPassInvoiceRecognitionService(loader(),
                options -> new RecordingEngine(seenByEngine), parser,
                RecognitionPlan.singlePass(image -> preprocessed),
                new InvoiceDataMerger(), 0.80).recognize(DOCUMENT);

        assertSame(preprocessed, seenByEngine.get());
    }

    @Test
    @DisplayName("each pass prepares the image its own way")
    void eachPassPreparesItsOwnImage() {
        List<String> prepared = new ArrayList<>();
        RecognitionPlan plan = new RecognitionPlan(List.of(
                RecognitionPass.of("first", image -> {
                    prepared.add("first");
                    return image;
                }),
                RecognitionPass.of("second", image -> {
                    prepared.add("second");
                    return image;
                })));

        new MultiPassInvoiceRecognitionService(loader(),
                new ScriptedEngines(List.of(DAMAGED, DAMAGED)), parser, plan,
                new InvoiceDataMerger(), 0.99).recognize(DOCUMENT);

        assertEquals(List.of("first", "second"), prepared);
    }

    @Test
    @DisplayName("closes the engine even when recognition throws")
    void closesTheEngineOnFailure() {
        FailingEngine engine = new FailingEngine();

        assertThrows(IllegalStateException.class, () -> new MultiPassInvoiceRecognitionService(
                loader(), options -> engine, parser,
                RecognitionPlan.singlePass(ImagePreprocessor.identity()),
                new InvoiceDataMerger(), 0.80).recognize(DOCUMENT));

        assertTrue(engine.isClosed());
    }

    @Test
    @DisplayName("releases the engine after every pass, not only the last")
    void closesEveryEngine() {
        List<FixedTextOcrEngine> created = new ArrayList<>();
        OcrEngineFactory counting = options -> {
            FixedTextOcrEngine engine = new FixedTextOcrEngine(DAMAGED);
            created.add(engine);
            return engine;
        };

        new MultiPassInvoiceRecognitionService(loader(), counting, parser, planOf(3),
                new InvoiceDataMerger(), 0.99).recognize(DOCUMENT);

        assertEquals(3, created.size());
        assertTrue(created.stream().allMatch(engine -> engine.closeCount() == 1),
                "a native handle left open once per page is a leak per page");
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
                () -> new MultiPassInvoiceRecognitionService(failing,
                        options -> new FixedTextOcrEngine("unused"), parser,
                        RecognitionPlan.singlePass(ImagePreprocessor.identity()),
                        new InvoiceDataMerger(), 0.80).recognize(DOCUMENT));

        assertEquals("cannot read invoice.png", failure.getMessage());
    }

    /** Remembers the image it was handed, so the pipeline order can be asserted. */
    private static final class RecordingEngine implements OcrEngine {

        private final AtomicReference<SourceImage> seen;

        RecordingEngine(AtomicReference<SourceImage> seen) {
            this.seen = seen;
        }

        @Override
        public RecognizedText recognize(SourceImage image) {
            seen.set(image);
            return RecognizedText.of(CLEAN);
        }
    }

    /** Fails during recognition, so the try-with-resources can be observed. */
    private static final class FailingEngine implements OcrEngine {

        private boolean closed;

        @Override
        public RecognizedText recognize(SourceImage image) {
            throw new IllegalStateException("engine blew up");
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
