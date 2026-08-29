package com.invoiceocr.service;

import com.invoiceocr.domain.InvoiceData;
import com.invoiceocr.domain.RecognizedText;
import com.invoiceocr.domain.SourceImage;
import com.invoiceocr.extraction.InvoiceParser;
import com.invoiceocr.image.DocumentLoader;
import com.invoiceocr.image.ImagePreprocessor;
import com.invoiceocr.ocr.OcrEngine;
import com.invoiceocr.ocr.OcrEngineFactory;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Wires the four pipeline stages together: load, preprocess, recognise, parse.
 *
 * <p>Every stage arrives as a collaborator, so this class contains no format,
 * no engine and no regex knowledge - only the order of operations.</p>
 */
public final class DefaultInvoiceRecognitionService implements InvoiceRecognitionService {

    private static final Logger LOG = System.getLogger(DefaultInvoiceRecognitionService.class.getName());

    private final DocumentLoader documentLoader;
    private final ImagePreprocessor preprocessor;
    private final OcrEngineFactory engineFactory;
    private final InvoiceParser parser;

    public DefaultInvoiceRecognitionService(DocumentLoader documentLoader,
                                            ImagePreprocessor preprocessor,
                                            OcrEngineFactory engineFactory,
                                            InvoiceParser parser) {
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader");
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public InvoiceData recognize(Path documentPath) {
        Objects.requireNonNull(documentPath, "documentPath");
        long startedAt = System.nanoTime();

        SourceImage loaded = documentLoader.load(documentPath);
        SourceImage prepared = preprocessor.apply(loaded);
        RecognizedText text = recognizeText(prepared);
        InvoiceData data = parser.parse(text);

        LOG.log(Level.INFO, () -> String.format(
                "Recognised %s in %d ms: %d of %d fields found",
                documentPath.getFileName(),
                (System.nanoTime() - startedAt) / 1_000_000,
                data.recognizedCount(),
                data.fields().size()));
        return data;
    }

    private RecognizedText recognizeText(SourceImage image) {
        try (OcrEngine engine = engineFactory.create()) {
            LOG.log(Level.DEBUG, () -> "Running " + engine.name() + " on " + image.width() + "x" + image.height());
            return engine.recognize(image);
        }
    }
}
