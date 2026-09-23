package io.forgeloop.control.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects missing or oversized artifact lengths before Spring allocates the request body. */
@Component
public class ArtifactUploadSizeFilter extends OncePerRequestFilter {
    private final long maxBytes;
    public ArtifactUploadSizeFilter(@Value("${forgeloop.artifacts.max-bytes:1048576}") long maxBytes) {
        this.maxBytes = maxBytes;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/runner/artifacts".equals(request.getRequestURI()) || !"POST".equals(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0) {
            response.sendError(HttpStatus.LENGTH_REQUIRED.value(), "Content-Length is required");
            return;
        }
        if (length > maxBytes) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Artifact exceeds the configured size limit");
            return;
        }
        chain.doFilter(request, response);
    }
}
