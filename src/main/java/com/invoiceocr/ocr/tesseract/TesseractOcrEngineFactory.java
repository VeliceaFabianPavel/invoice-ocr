package com.invoiceocr.ocr.tesseract;

import com.invoiceocr.config.OcrSettings;
import com.invoiceocr.ocr.OcrEngine;
import com.invoiceocr.ocr.OcrEngineFactory;
import java.util.Objects;
import java.util.function.Supplier;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

/** Validates the installation, then hands out a configured {@link TesseractOcrEngine}. */
public final class TesseractOcrEngineFactory implements OcrEngineFactory {

    private final OcrSettings settings;
    private final TesseractInstallation installation;
    private final Supplier<ITesseract> instanceSupplier;

    public TesseractOcrEngineFactory(OcrSettings settings) {
        this(settings, new TesseractInstallation(settings), Tesseract::new);
    }

    public TesseractOcrEngineFactory(OcrSettings settings,
                                     TesseractInstallation installation,
                                     Supplier<ITesseract> instanceSupplier) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.installation = Objects.requireNonNull(installation, "installation");
        this.instanceSupplier = Objects.requireNonNull(instanceSupplier, "instanceSupplier");
    }

    @Override
    public OcrEngine create() {
        installation.verify();
        return new TesseractOcrEngine(instanceSupplier.get(), settings);
    }
}
