package com.invoiceocr.ui;

import java.nio.file.Path;
import java.util.Optional;

/** Asks the user for a document; empty means the selection was cancelled. */
@FunctionalInterface
public interface DocumentChooser {

    Optional<Path> choose();
}
