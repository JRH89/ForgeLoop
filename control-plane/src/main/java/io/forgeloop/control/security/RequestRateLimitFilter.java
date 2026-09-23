package io.forgeloop.control.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds request bursts per authenticated runner or network peer. This is deliberately a
 * control-plane safety net; production ingress must also enforce connection and body limits.
 */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;

    @Autowired
    public RequestRateLimitFilter(@Value("${forgeloop.http.requests-per-minute:600}") int requestsPerMinute) {
        this(requestsPerMinute, Clock.systemUTC());
    }

    RequestRateLimitFilter(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute < 1 || requestsPerMinute > 100_000) throw new IllegalArgumentException("HTTP rate limit is invalid");
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = Instant.now(clock).getEpochSecond() / 60;
        String runner = request.getHeader("X-ForgeLoop-Runner-Id");
        String client = runner == null || runner.isBlank() ? request.getRemoteAddr() : "runner:" + runner;
        String key = client + ":" + routeGroup(request.getRequestURI());
        if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(key)) removeExpired(minute);
        if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(key)) {
            reject(response, 60);
            return;
        }
        Window window = windows.compute(key, (ignored, current) -> current == null || current.minute != minute
                ? new Window(minute, 1) : new Window(minute, current.count + 1));
        if (window.count > requestsPerMinute) {
            reject(response, Math.max(1, 60 - (Instant.now(clock).getEpochSecond() % 60)));
            return;
        }
        chain.doFilter(request, response);
    }

    private void removeExpired(long minute) { windows.entrySet().removeIf(entry -> entry.getValue().minute < minute); }
    private static String routeGroup(String uri) {
        if (uri.startsWith("/api/github/webhooks")) return "webhook";
        if (uri.startsWith("/api/runner")) return "runner";
        return "operator";
    }
    private static void reject(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
    }
    private record Window(long minute, int count) { }
}
