package io.forgeloop.control.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Same-origin session probe used by the web gateway before it serves the operator shell. */
@RestController
@RequestMapping("/api/auth")
public class BrowserSessionController {
    private final OperatorContext operators;
    public BrowserSessionController(OperatorContext operators) { this.operators = operators; }
    @GetMapping("/session") public ResponseEntity<Void> session(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken) && !(authentication instanceof JwtAuthenticationToken)) {
            return ResponseEntity.status(401).build();
        }
        operators.organizationId();
        return ResponseEntity.noContent().build();
    }
}
