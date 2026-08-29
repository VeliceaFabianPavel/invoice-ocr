package com.invoiceocr.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * {@link TaskExecutor} backed by {@link SwingWorker}: the task runs on a worker
 * thread and both callbacks fire on the event dispatch thread.
 */
public final class SwingTaskExecutor implements TaskExecutor {

    @Override
    public <T> void execute(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        new SwingWorker<T, Void>() {

            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onFailure.accept(e);
                } catch (ExecutionException e) {
                    onFailure.accept(e.getCause() != null ? e.getCause() : e);
                }
            }
        }.execute();
    }
}
