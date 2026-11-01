package com.makeasaying.engine.async;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Worker thread pool per AGENTS.md section 7.1 and M1 brief.
 * Executes background tasks off the main thread; results are queued and drained on the main simulation thread.
 */
public final class Jobs {
    private final ExecutorService executor;
    private final Queue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public Jobs(int poolSize) {
        int threads = Math.clamp(poolSize, 2, 4);
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "Worker-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
    }

    public Jobs() {
        this(2);
    }

    /**
     * Submits a callable task. When complete, result is delivered to onDone on the main thread.
     * If an error occurs and onError is provided, it is invoked on the main thread; otherwise, logged loudly.
     */
    public <T> void submit(Callable<T> task, Consumer<T> onDone, Consumer<Throwable> onError) {
        if (isShutdown.get()) {
            Log.warn(LogCategory.ENGINE, "Rejected job submission: worker pool is already shut down.");
            return;
        }

        executor.submit(() -> {
            try {
                T result = task.call();
                mainThreadQueue.add(() -> {
                    try {
                        if (onDone != null) {
                            onDone.accept(result);
                        }
                    } catch (Throwable callbackError) {
                        Log.error(LogCategory.ENGINE, "Exception inside Jobs onDone callback: %s",
                            callbackError, callbackError.getMessage());
                    }
                });
            } catch (Throwable taskError) {
                mainThreadQueue.add(() -> {
                    try {
                        if (onError != null) {
                            onError.accept(taskError);
                        } else {
                            Log.error(LogCategory.ENGINE, "Worker job failed: %s",
                                taskError, taskError.getMessage());
                        }
                    } catch (Throwable handlerError) {
                        Log.error(LogCategory.ENGINE, "Exception inside Jobs onError callback: %s",
                            handlerError, handlerError.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Overload for void-returning tasks.
     */
    public void submit(Runnable task, Runnable onDone, Consumer<Throwable> onError) {
        submit(() -> {
            task.run();
            return null;
        }, ignored -> {
            if (onDone != null) onDone.run();
        }, onError);
    }

    /**
     * Drains all pending completion and error handlers on the main thread.
     * Must be called once per frame in the main game loop before simulation ticks.
     */
    public void drain() {
        Runnable action;
        while ((action = mainThreadQueue.poll()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                Log.error(LogCategory.ENGINE, "Unexpected error draining job action: %s", t, t.getMessage());
            }
        }
    }

    public int getPendingMainThreadCount() {
        return mainThreadQueue.size();
    }

    public boolean isShutdown() {
        return isShutdown.get();
    }

    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
