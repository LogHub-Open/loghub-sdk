package io.loghub.logger.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.loghub.contract.LogEvent;
import io.loghub.logger.config.LogHubConfig;
import io.loghub.logger.converter.LogEventConverter;
import io.loghub.logger.http.LogHubHttpClient;
import io.loghub.logger.queue.LogEventQueue;

/**
 * Logback Appender that sends structured logs to LogHub API.
 *
 * <p>This appender:
 * <ul>
 *   <li>Captures INFO, WARN, and ERROR level logs by default</li>
 *   <li>Sends logs asynchronously to avoid blocking the application</li>
 *   <li>Never throws exceptions that could impact the application</li>
 *   <li>Enriches logs with application metadata</li>
 *   <li>Supports API Key authentication via X-API-KEY header</li>
 * </ul>
 *
 * <p>Configuration example in logback.xml:
 * <pre>{@code
 * <appender name="LOGHUB" class="io.loghub.logger.appender.HttpLogAppender">
 *     <endpoint>http://api.loghub.io/api/logs</endpoint>
 *     <application>my-service</application>
 *     <environment>production</environment>
 *     <apiKey>your-api-key</apiKey>
 *     <timeoutMs>5000</timeoutMs>
 *     <queueCapacity>1000</queueCapacity>
 *     <minimumLevel>INFO</minimumLevel>
 * </appender>
 * }</pre>
 *
 * <p>API Key Resolution (in order of priority):
 * <ol>
 *   <li>Explicitly configured via {@code <apiKey>} in logback.xml</li>
 *   <li>System property: {@code -Dloghub.api.key=your-key}</li>
 *   <li>Environment variable: {@code LOGHUB_API_KEY}</li>
 * </ol>
 *
 * <p>For production environments, it's recommended to use environment variables
 * to avoid exposing the API key in configuration files.
 */
public class HttpLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // Configuration properties (set via logback.xml)
    private String endpoint;
    private String application = "unknown";
    private String environment = "unknown";
    private String apiKey;
    private int timeoutMs = 5000;
    private int queueCapacity = 1000;
    private int workerThreads = 1;
    private boolean enabled = true;
    private Level minimumLevel = Level.INFO;

    // Internal components (initialized on start)
    private LogHubConfig config;
    private LogHubHttpClient httpClient;
    private LogEventQueue eventQueue;
    private LogEventConverter converter;

    @Override
    public void start() {
        if (!enabled) {
            addInfo("LogHub appender is disabled");
            super.start(); // Mark as started even when disabled to avoid errors
            return;
        }

        if (endpoint == null || endpoint.isBlank()) {
            addError("LogHub endpoint is required but not configured");
            // Still mark as started to prevent Spring Boot startup failure
            super.start();
            return;
        }

        try {
            addInfo("Initializing LogHub appender with endpoint: " + endpoint);

            // Resolve API Key from configuration or environment variable
            String resolvedApiKey = resolveApiKey();

            // Initialize configuration (one instance per appender, not shared)
            config = new LogHubConfig();
            config.setApplication(application);
            config.setEnvironment(environment);
            config.setEndpoint(endpoint);
            config.setApiKey(resolvedApiKey);
            config.setTimeoutMs(timeoutMs);
            config.setQueueCapacity(queueCapacity);
            config.setWorkerThreads(workerThreads);
            config.setEnabled(enabled);

            // Initialize components with API Key
            httpClient = new LogHubHttpClient(endpoint, timeoutMs, resolvedApiKey);
            eventQueue = new LogEventQueue(httpClient, queueCapacity, workerThreads);
            converter = new LogEventConverter(config);

            // Start the async queue
            eventQueue.start();

            super.start();
            addInfo("LogHub appender started successfully - endpoint: " + endpoint);

        } catch (Exception e) {
            addError("Failed to start LogHub appender: " + e.getMessage(), e);
            // Mark as started anyway to prevent blocking application startup
            // The appender will simply not send logs if initialization failed
            super.start();
        }
    }

    @Override
    public void stop() {
        try {
            if (eventQueue != null) {
                eventQueue.stop();
            }
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            // Silently ignore shutdown errors
        }
        super.stop();
        addInfo("LogHub appender stopped");
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!isStarted() || !enabled) {
            return;
        }

        // Check if components were initialized successfully
        if (converter == null || eventQueue == null) {
            return;
        }

        try {
            // Filter by minimum level
            if (!isLevelEnabled(eventObject.getLevel())) {
                return;
            }

            // Convert and enqueue the event
            LogEvent logEvent = converter.convert(eventObject);
            eventQueue.enqueue(logEvent);

        } catch (Exception e) {
            // Never throw - silently ignore errors
            // Avoid recursive logging by not using addError here
        }
    }

    /**
     * Checks if the given level meets the minimum level threshold.
     *
     * @param level the log level to check
     * @return true if the level should be logged
     */
    private boolean isLevelEnabled(Level level) {
        if (level == null) {
            return false;
        }
        return level.isGreaterOrEqual(minimumLevel);
    }

    /**
     * Resolves the API Key from multiple sources in order of priority:
     * 1. Explicitly configured in logback.xml
     * 2. System property: loghub.api.key
     * 3. Environment variable: LOGHUB_API_KEY
     *
     * @return the resolved API key, or null if not configured
     */
    private String resolveApiKey() {
        // Priority 1: Explicitly configured
        if (apiKey != null && !apiKey.isBlank()) {
            addInfo("Using API Key from logback.xml configuration");
            return apiKey;
        }

        // Priority 2: System property
        String systemPropKey = System.getProperty("loghub.api.key");
        if (systemPropKey != null && !systemPropKey.isBlank()) {
            addInfo("Using API Key from system property 'loghub.api.key'");
            return systemPropKey;
        }

        // Priority 3: Environment variable
        String envKey = System.getenv("LOGHUB_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            addInfo("Using API Key from environment variable 'LOGHUB_API_KEY'");
            return envKey;
        }

        addWarn("No API Key configured. Requests to LogHub API may fail with 401 Unauthorized.");
        return null;
    }

    // ========== Configuration Setters (called by Logback) ==========

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMinimumLevel(String level) {
        this.minimumLevel = Level.toLevel(level, Level.INFO);
    }

    // ========== Configuration Getters ==========

    public String getEndpoint() {
        return endpoint;
    }

    public String getApplication() {
        return application;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getApiKey() {
        // Return masked for security
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****";
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMinimumLevel() {
        return minimumLevel.toString();
    }
}

