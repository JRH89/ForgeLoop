package io.forgeloop.control.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;

/** Same-origin session probe used by the web gateway before it serves the operator shell. */
@RestController
@RequestMapping("/api/auth")
public class BrowserSessionController {
    private final OperatorContext operators;
    private final boolean developmentMode;
    public BrowserSessionController(OperatorContext operators,
                                    @Value("${forgeloop.security.mode:production}") String securityMode) {
        this.operators = operators;
        this.developmentMode = "development".equals(securityMode);
    }
    @GetMapping("/session") public ResponseEntity<Void> session(Authentication authentication) {
        if (!developmentMode && !(authentication instanceof OAuth2AuthenticationToken) && !(authentication instanceof JwtAuthenticationToken)) {
            return ResponseEntity.status(401).build();
        }
        operators.organizationId();
        return ResponseEntity.noContent().build();
    }
}
