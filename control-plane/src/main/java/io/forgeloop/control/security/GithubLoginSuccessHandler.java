package io.forgeloop.control.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Completes server-side membership resolution before an OAuth session reaches the console. */
@Component
public class GithubLoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(GithubLoginSuccessHandler.class);
    private final GithubLoginProvisioner provisioner;
    public GithubLoginSuccessHandler(GithubLoginProvisioner provisioner) { this.provisioner = provisioner; }
    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) throw new ServletException("GitHub OAuth authentication was expected");
        try {
            Object githubId = oauth.getPrincipal().getAttribute("id");
            provisioner.requireMembership(String.valueOf(githubId));
            response.sendRedirect("/app");
        } catch (RuntimeException denied) {
            log.warn("GitHub OAuth identity was authenticated but ForgeLoop membership provisioning failed: {}", denied.getMessage(), denied);
            request.getSession().invalidate();
            response.sendRedirect("/?login=failed");
        }
    }
}
