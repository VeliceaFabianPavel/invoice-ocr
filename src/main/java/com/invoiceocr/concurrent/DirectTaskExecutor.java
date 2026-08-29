package com.invoiceocr.concurrent;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Executes the task inline on the calling thread.
 *
 * <p>Intended for tests and headless batch use, where asynchrony would only add
 * timing noise.</p>
 */
public final class DirectTaskExecutor implements TaskExecutor {

    @Override
    public <T> void execute(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        T result;
        try {
            result = task.call();
        } catch (Exception e) {
            onFailure.accept(e);
            return;
        }
        onSuccess.accept(result);
    }
}
