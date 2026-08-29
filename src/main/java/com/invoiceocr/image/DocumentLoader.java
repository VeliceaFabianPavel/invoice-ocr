package com.invoiceocr.image;

import com.invoiceocr.domain.SourceImage;
import java.nio.file.Path;

/**
 * Turns a file on disk into a decoded {@link SourceImage}.
 *
 * <p>Isolating decoding behind an interface keeps the OCR engine free of file
 * formats: a future PDF or scanner-feed loader plugs in here alone.</p>
 */
public interface DocumentLoader {

    /**
     * @throws com.invoiceocr.exception.DocumentLoadException if the file cannot be decoded
     * @throws com.invoiceocr.exception.UnsupportedDocumentException if the format is not handled
     */
    SourceImage load(Path path);

    /** Whether {@link #load(Path)} is expected to succeed for this path. */
    boolean supports(Path path);
}
