package com.paytm.wallet.util;

import static net.logstash.logback.argument.StructuredArguments.entries;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EntryLogger {

    private static final Logger log = LoggerFactory.getLogger(EntryLogger.class);

    public void log(Class<?> type, String method) {
        log(type, method, Map.of());
    }

    public void log(Class<?> type, String method, Map<String, ?> payload) {
        Instant enteredAt = Instant.now();

        log.info(
                "Entering {}.{}() with payload - {}",
                type.getSimpleName(),
                method,
                entries(payload)
        );
    }

    public void event(String event, Map<String, ?> fields) {
        Instant occurredAt = Instant.now();

        log.info(
                "Event: {}",
                event,
                keyValue("event", event),
                keyValue("occurred_at", occurredAt),
                entries(fields)
        );
    }

    public String callerFingerprint(String caller) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(caller.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}