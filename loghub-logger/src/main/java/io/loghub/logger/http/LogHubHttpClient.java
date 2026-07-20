package io.loghub.logger.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.loghub.contract.LogEvent;
import io.loghub.logger.config.LogHubConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * HTTP client for sending log events to the LogHub API.
 * Uses Java's native HttpClient for async communication.
 */
public class LogHubHttpClient {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final Duration timeout;

    /**
     * Creates a new HTTP client.
     *
     * @param endpoint  the LogHub API endpoint URL
     * @param timeoutMs the request timeout in milliseconds
     */
    public LogHubHttpClient(String endpoint, int timeoutMs) {
        this(endpoint, timeoutMs, null);
    }

    /**
     * Creates a new HTTP client with API Key authentication.
     *
     * @param endpoint  the LogHub API endpoint URL
     * @param timeoutMs the request timeout in milliseconds
     * @param apiKey    the API key for authentication (can be null)
     */
    public LogHubHttpClient(String endpoint, int timeoutMs, String apiKey) {
        this.endpoint = endpoint;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.apiKey = apiKey;

        // Dedicated executor so response handling never competes with the host
        // application for ForkJoinPool.commonPool(), which HttpClient uses by default.
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "loghub-http-client");
            thread.setDaemon(true);
            return thread;
        };
        this.executorService = Executors.newFixedThreadPool(2, threadFactory);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .executor(executorService)
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Sends a log event asynchronously to the LogHub API.
     *
     * @param logEvent the log event to send
     * @return a CompletableFuture that completes when the request is done
     */
    public CompletableFuture<Void> sendAsync(LogEvent logEvent) {
        try {
            String json = objectMapper.writeValueAsString(logEvent);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", CONTENT_TYPE_JSON);

            // Add API Key header if configured
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header(LogHubConfig.API_KEY_HEADER, apiKey);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        // Log response status for debugging (non-blocking)
                        // We don't throw on error status to avoid impacting the application
                    })
                    .exceptionally(throwable -> {
                        // Silently handle exceptions - we never want to impact the application
                        return null;
                    });

        } catch (JsonProcessingException e) {
            // Return completed future on serialization error
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        executorService.shutdown();
    }
}

