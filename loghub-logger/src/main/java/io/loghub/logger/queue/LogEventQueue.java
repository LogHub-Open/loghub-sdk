package io.loghub.logger.queue;

import io.loghub.contract.LogEvent;
import io.loghub.logger.http.LogHubHttpClient;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous queue manager for log events.
 * Handles buffering and async dispatch of logs to avoid blocking the application.
 */
public final class LogEventQueue {

    private final BlockingQueue<LogEvent> queue;
    private final ExecutorService executorService;
    private final LogHubHttpClient httpClient;
    private final AtomicBoolean running;
    private final AtomicBoolean started;
    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * Creates a new log event queue.
     *
     * @param httpClient    the HTTP client for sending logs
     * @param queueCapacity the maximum queue capacity
     * @param workerThreads the number of worker threads
     */
    public LogEventQueue(LogHubHttpClient httpClient, int queueCapacity, int workerThreads) {
        this.httpClient = httpClient;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.running = new AtomicBoolean(false);
        this.started = new AtomicBoolean(false);

        // Create daemon threads so they don't prevent JVM shutdown
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "loghub-sender");
            thread.setDaemon(true);
            return thread;
        };

        this.executorService = Executors.newFixedThreadPool(workerThreads, threadFactory);
    }

    /**
     * Starts the queue processing workers.
     */
    public synchronized void start() {
        if (started.compareAndSet(false, true)) {
            running.set(true);
            executorService.submit(this::processQueue);
        }
    }

    /**
     * Enqueues a log event for async sending.
     * If the queue is full, the event is silently dropped to avoid blocking.
     *
     * @param logEvent the log event to enqueue
     * @return true if the event was enqueued, false if dropped
     */
    public boolean enqueue(LogEvent logEvent) {
        if (!running.get()) {
            droppedCount.incrementAndGet();
            return false;
        }

        // Non-blocking offer - drop if queue is full
        boolean enqueued = queue.offer(logEvent);
        if (!enqueued) {
            droppedCount.incrementAndGet();
        }
        return enqueued;
    }

    /**
     * Stops the queue and releases resources.
     * Attempts to process remaining events with a timeout.
     */
    public void stop() {
        running.set(false);
        started.set(false);

        executorService.shutdown();
        try {
            // Wait briefly for in-flight events
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Worker method that continuously processes the queue.
     */
    private void processQueue() {
        while (running.get() || !queue.isEmpty()) {
            try {
                LogEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    sendEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Never throw - silently continue processing
            }
        }
    }

    /**
     * Sends a single event via HTTP client.
     * Failures are silently ignored to prevent impact on the application.
     *
     * @param event the event to send
     */
    private void sendEvent(LogEvent event) {
        try {
            httpClient.sendAsync(event);
        } catch (Exception e) {
            // Silently ignore - we never want to impact the application
        }
    }

    /**
     * Gets the current queue size.
     *
     * @return the number of events in the queue
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Checks if the queue is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the total number of log events dropped because the queue was full
     * or not running, since this queue was created.
     *
     * @return the dropped event count
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }
}

