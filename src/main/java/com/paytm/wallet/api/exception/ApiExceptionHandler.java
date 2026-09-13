package com.paytm.wallet.api.exception;

import com.paytm.wallet.util.EntryLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final EntryLogger entryLogger;

    public ApiExceptionHandler(EntryLogger entryLogger) {
        this.entryLogger = entryLogger;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException exception, HttpServletRequest request) {
        entryLogger.log(getClass(), "api", Map.of("status", exception.status().value(), "path", request.getRequestURI()));
        return ResponseEntity.status(exception.status()).body(body(exception.status().value(), exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        entryLogger.log(getClass(), "invalid", Map.of("path", request.getRequestURI()));
        return ResponseEntity.badRequest().body(body(400, "invalid request", request));
    }

    private Map<String, Object> body(int status, String message, HttpServletRequest request) {
        return Map.of("timestamp", Instant.now().toString(), "status", status, "error", message,
                "correlation_id", String.valueOf(request.getAttribute("correlationId")));
    }
}
