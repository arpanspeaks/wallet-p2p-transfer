package com.paytm.wallet.security;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class BearerAuthFilter implements Filter {
    private final EntryLogger entryLogger;

    public BearerAuthFilter(EntryLogger entryLogger) {
        this.entryLogger = entryLogger;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        entryLogger.log(getClass(), "doFilter", Map.of("method", httpRequest.getMethod(), "path", httpRequest.getRequestURI()));
        if (isPublicEndpoint(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setContentType("application/json");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"status\":401,\"error\":\"Bearer token required\"}");
            return;
        }
        request.setAttribute("caller", new CallerIdentity(header.substring(7)));
        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
