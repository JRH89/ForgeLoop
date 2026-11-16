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
 * Bounds request bursts per network peer and, on runner routes, per runner. This is deliberately a
 * control-plane safety net; production ingress must also enforce connection and body limits.
 *
 * <p>The runner header is unauthenticated at this point in the chain, so it never replaces the peer
 * address: every request is first charged to its peer, which stops header rotation from bypassing
 * the limit or flooding the window table.</p>
 */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    /** Several runners may share one NAT address, so the per-peer runner allowance is larger. */
    static final int RUNNER_PEER_MULTIPLIER = 10;
    private static final java.util.regex.Pattern RUNNER_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");
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
        String group = routeGroup(request.getRequestURI());
        boolean runnerRoute = "runner".equals(group);
        // The peer window always applies and cannot be influenced by request headers.
        if (!admit("peer:" + request.getRemoteAddr() + ":" + group,
                runnerRoute ? requestsPerMinute * RUNNER_PEER_MULTIPLIER : requestsPerMinute, minute, response)) return;
        String runner = request.getHeader("X-ForgeLoop-Runner-Id");
        // Well-formed runner IDs also get an individual window so one runner cannot starve its NAT neighbours.
        if (runnerRoute && runner != null && RUNNER_ID.matcher(runner).matches()
                && !admit("runner:" + runner, requestsPerMinute, minute, response)) return;
        chain.doFilter(request, response);
    }

    /** Counts one request against a window, writing the 429 response and returning false when it is exhausted. */
    private boolean admit(String key, int limit, long minute, HttpServletResponse response) throws IOException {
        if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(key)) removeExpired(minute);
        if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(key)) {
            reject(response, 60);
            return false;
        }
        Window window = windows.compute(key, (ignored, current) -> current == null || current.minute != minute
                ? new Window(minute, 1) : new Window(minute, current.count + 1));
        if (window.count > limit) {
            reject(response, Math.max(1, 60 - (Instant.now(clock).getEpochSecond() % 60)));
            return false;
        }
        return true;
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
