package com.invoiceocr.service;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.ocr.OcrEngine;
import com.invoiceocr.ocr.OcrEngineFactory;
import com.invoiceocr.recognition.InvoiceDataMerger;
import com.invoiceocr.recognition.PassOutcome;
import com.invoiceocr.recognition.RecognitionPass;
import com.invoiceocr.recognition.RecognitionPlan;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the same page more than once, differently, and keeps the best of what
 * comes back.
 *
 * <p>This is the change that the rest of 1.2 is built to exploit. A single OCR
 * pass has to commit, before it has seen anything, to one preparation of the
 * image and one assumption about the layout. When either guess is wrong the
 * fields it loses are lost for good, and nothing downstream can recover them —
 * no pattern, however clever, can read text that was never transcribed.</p>
 *
 * <p>Running the page again with a different preparation puts different text in
 * front of the same rules, and the answers can then be compared. Because the
 * mistakes OCR makes belong to the rendering rather than to the page, two
 * preparations rarely make the same one, and a value that survives both is very
 * likely right.</p>
 *
 * <p>The cost is bounded at both ends. The image is decoded once and reused, so
 * a pass is an OCR call and nothing more; and the ladder stops the moment a
 * result is good enough, which on a clean scan is after the first pass. Paying
 * for four readings is reserved for the pages that need four.</p>
 */
public final class MultiPassInvoiceRecognitionService implements InvoiceRecognitionService {

    private static final Logger LOG =
            System.getLogger(MultiPassInvoiceRecognitionService.class.getName());

    private final DocumentLoader documentLoader;
    private final OcrEngineFactory engineFactory;
    private final InvoiceParser parser;
    private final RecognitionPlan plan;
    private final InvoiceDataMerger merger;
    private final double targetConfidence;

    public MultiPassInvoiceRecognitionService(DocumentLoader documentLoader,
                                              OcrEngineFactory engineFactory,
                                              InvoiceParser parser,
                                              RecognitionPlan plan,
                                              InvoiceDataMerger merger,
                                              double targetConfidence) {
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.targetConfidence = targetConfidence;
    }

    @Override
    public InvoiceData recognize(Path documentPath) {
        Objects.requireNonNull(documentPath, "documentPath");
        long startedAt = System.nanoTime();

        SourceImage loaded = documentLoader.load(documentPath);
        List<PassOutcome> outcomes = new ArrayList<>(plan.size());

        for (RecognitionPass pass : plan.passes()) {
            PassOutcome outcome = run(pass, loaded);
            outcomes.add(outcome);
            LOG.log(Level.DEBUG, () -> "  " + outcome);
            if (isSatisfactory(outcome.data())) {
                break;
            }
        }

        InvoiceData merged = merger.merge(outcomes);
        int passesRun = outcomes.size();
        LOG.log(Level.INFO, () -> String.format(
                "Recognised %s in %d ms over %d pass%s: %d of %d fields, %d line items",
                documentPath.getFileName(),
                (System.nanoTime() - startedAt) / 1_000_000,
                passesRun,
                passesRun == 1 ? "" : "es",
                merged.recognizedCount(),
                merged.fields().size(),
                merged.lineItems().size()));
        return merged;
    }

    private PassOutcome run(RecognitionPass pass, SourceImage loaded) {
        long startedAt = System.nanoTime();
        SourceImage prepared = pass.preprocessor().apply(loaded);
        RecognizedText text = recognizeText(prepared, pass);
        InvoiceData data = parser.parse(text);
        return new PassOutcome(pass, data, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private RecognizedText recognizeText(SourceImage image, RecognitionPass pass) {
        try (OcrEngine engine = engineFactory.create(pass.options())) {
            LOG.log(Level.DEBUG, () -> "Running " + engine.name() + " for pass " + pass.name()
                    + " on " + image.width() + "x" + image.height());
            return engine.recognize(image);
        }
    }

    /**
     * Whether there is anything left for another pass to improve.
     *
     * <p>Two conditions, and both are about trust rather than completeness. The
     * mean confidence has to clear the target, and no field may be flagged for
     * review — because a page with one shaky figure is exactly the page a second
     * rendering is likely to settle, and stopping there would waste the one thing
     * that could settle it.</p>
     *
     * <p>Fields the invoice does not carry are not held against it. An invoice
     * that prints no IBAN is complete without one, and waiting for a field that
     * does not exist would run every pass on every page.</p>
     */
    boolean isSatisfactory(InvoiceData data) {
        return data.recognizedCount() > 0
                && data.averageConfidence() >= targetConfidence
                && data.needingReview().isEmpty();
    }
}
