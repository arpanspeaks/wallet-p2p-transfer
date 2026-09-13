package com.paytm.wallet.config;

import com.paytm.wallet.util.EntryLogger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {
    private final EntryLogger entryLogger;

    public CorrelationIdFilter(EntryLogger entryLogger) {
        this.entryLogger = entryLogger;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String correlationId = httpRequest.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        request.setAttribute("correlationId", correlationId);
        MDC.put("correlation_id", correlationId);
        ((HttpServletResponse) response).setHeader("X-Correlation-Id", correlationId);
        entryLogger.log(getClass(), "doFilter", Map.of("method", httpRequest.getMethod(), "path", httpRequest.getRequestURI()));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlation_id");
        }
    }
}
