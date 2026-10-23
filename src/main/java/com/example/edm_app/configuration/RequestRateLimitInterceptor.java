package com.example.edm_app.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestRateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Rule rule = ruleFor(request);
        if (rule == null) {
            return true;
        }

        String subject = rateLimitSubject(request, rule);
        String key = rule.key + ":" + subject;
        long now = System.currentTimeMillis();
        if (!windows.containsKey(key) && windows.size() >= 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt > Duration.ofHours(1).toMillis());
            if (windows.size() >= 10_000) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"message\":\"Request limit temporarily unavailable\"}");
                return false;
            }
        }
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= rule.windowMillis) {
                return new Window(now, new AtomicInteger(1));
            }
            current.count.incrementAndGet();
            return current;
        });

        if (requestsSinceCleanup.incrementAndGet() % 256 == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt > Duration.ofHours(1).toMillis());
        }

        if (window.count.get() > rule.limit) {
            long retrySeconds = Math.max(1, (rule.windowMillis - (now - window.startedAt) + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retrySeconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests; try again later\"}");
            return false;
        }
        return true;
    }

    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && "/auth/login".equals(path)) {
            return new Rule("login", 10, Duration.ofMinutes(1).toMillis(), false);
        }
        if ("POST".equals(method) && "/auth/register".equals(path)) {
            return new Rule("register", 5, Duration.ofHours(1).toMillis(), false);
        }
        if ("POST".equals(method) && "/tracks/upload".equals(path)) {
            return new Rule("upload", 5, Duration.ofMinutes(10).toMillis(), true);
        }
        return null;
    }

    private String rateLimitSubject(HttpServletRequest request, Rule rule) {
        if (rule.authenticatedUser) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String email) {
                return email;
            }
        }
        return request.getRemoteAddr();
    }

    private record Rule(String key, int limit, long windowMillis, boolean authenticatedUser) { }
    private record Window(long startedAt, AtomicInteger count) { }
}
