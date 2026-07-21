package io.loghub.logger.converter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import io.loghub.contract.LogEvent;
import io.loghub.contract.LogLevel;
import io.loghub.logger.config.LogHubConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogEventConverter.
 */
class LogEventConverterTest {

    private LogHubConfig config;
    private LogEventConverter converter;

    @BeforeEach
    void setUp() {
        config = new LogHubConfig();
        config.setApplication("test-app");
        config.setEnvironment("test-env");
        converter = new LogEventConverter(config);
    }

    @Test
    void shouldConvertBasicEvent() {
        ILoggingEvent loggingEvent = createMockLoggingEvent(
                Level.INFO, "Test message", System.currentTimeMillis(), null, null);

        LogEvent event = converter.convert(loggingEvent);

        assertEquals("test-app", event.getApplication());
        assertEquals("test-env", event.getEnvironment());
        assertEquals(LogLevel.INFO, event.getLevel());
        assertEquals("Test message", event.getMessage());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getSdk());
        assertEquals("java", event.getSdk().getLanguage());
    }

    @Test
    void shouldMapAllLogLevels() {
        assertEquals(LogLevel.TRACE, convertLevel(Level.TRACE));
        assertEquals(LogLevel.DEBUG, convertLevel(Level.DEBUG));
        assertEquals(LogLevel.INFO, convertLevel(Level.INFO));
        assertEquals(LogLevel.WARN, convertLevel(Level.WARN));
        assertEquals(LogLevel.ERROR, convertLevel(Level.ERROR));
    }

    @Test
    void shouldExtractTraceIdFromMdc() {
        Map<String, String> mdc = new HashMap<>();
        mdc.put("traceId", "abc-123");

        ILoggingEvent loggingEvent = createMockLoggingEvent(
                Level.INFO, "Test", System.currentTimeMillis(), mdc, null);

        LogEvent event = converter.convert(loggingEvent);

        assertEquals("abc-123", event.getTraceId());
    }

    @Test
    void shouldExtractMetadataFromMdc() {
        Map<String, String> mdc = new HashMap<>();
        mdc.put("userId", "user-456");
        mdc.put("requestId", "req-789");

        ILoggingEvent loggingEvent = createMockLoggingEvent(
                Level.INFO, "Test", System.currentTimeMillis(), mdc, null);

        LogEvent event = converter.convert(loggingEvent);

        assertNotNull(event.getMetadata());
        assertEquals("user-456", event.getMetadata().get("userId"));
        assertEquals("req-789", event.getMetadata().get("requestId"));
    }

    @Test
    void shouldIncludeLoggerAndThreadInMetadata() {
        ILoggingEvent loggingEvent = createMockLoggingEvent(
                Level.INFO, "Test", System.currentTimeMillis(), new HashMap<>(), null);

        LogEvent event = converter.convert(loggingEvent);

        assertNotNull(event.getMetadata());
        assertNotNull(event.getMetadata().get("logger"));
        assertNotNull(event.getMetadata().get("thread"));
    }

    private LogLevel convertLevel(Level level) {
        ILoggingEvent event = createMockLoggingEvent(level, "msg", System.currentTimeMillis(), null, null);
        return converter.convert(event).getLevel();
    }

    private ILoggingEvent createMockLoggingEvent(Level level, String message, long timestamp,
                                                  Map<String, String> mdc, IThrowableProxy throwableProxy) {
        return new ILoggingEvent() {
            @Override
            public String getThreadName() {
                return "test-thread";
            }

            @Override
            public Level getLevel() {
                return level;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public Object[] getArgumentArray() {
                return new Object[0];
            }

            @Override
            public String getFormattedMessage() {
                return message;
            }

            @Override
            public String getLoggerName() {
                return "io.loghub.test.TestLogger";
            }

            @Override
            public ch.qos.logback.classic.spi.LoggerContextVO getLoggerContextVO() {
                return null;
            }

            @Override
            public IThrowableProxy getThrowableProxy() {
                return throwableProxy;
            }

            @Override
            public StackTraceElement[] getCallerData() {
                return new StackTraceElement[0];
            }

            @Override
            public boolean hasCallerData() {
                return false;
            }

            @Override
            public org.slf4j.Marker getMarker() {
                return null;
            }

            @Override
            public java.util.List<org.slf4j.Marker> getMarkerList() {
                return null;
            }

            @Override
            public Map<String, String> getMDCPropertyMap() {
                return mdc != null ? mdc : new HashMap<>();
            }

            @Override
            public Map<String, String> getMdc() {
                return getMDCPropertyMap();
            }

            @Override
            public long getTimeStamp() {
                return timestamp;
            }

            @Override
            public int getNanoseconds() {
                return 0;
            }

            @Override
            public long getSequenceNumber() {
                return 0;
            }

            @Override
            public void prepareForDeferredProcessing() {
            }

            @Override
            public java.util.List<org.slf4j.event.KeyValuePair> getKeyValuePairs() {
                return null;
            }
        };
    }
}

