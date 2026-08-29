package com.invoiceocr.concurrent;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs a piece of work away from the caller and reports back on the thread the
 * caller considers safe for UI updates.
 *
 * <p>OCR takes seconds; doing it on the Swing event thread freezes the window.
 * Hiding the threading model behind this interface keeps {@code SwingWorker} out
 * of the presenter and lets tests run everything synchronously.</p>
 */
public interface TaskExecutor {

    /**
     * @param task      the work to perform off the calling thread
     * @param onSuccess called with the result, on the UI thread
     * @param onFailure called with the failure, on the UI thread
     */
    <T> void execute(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure);
}
