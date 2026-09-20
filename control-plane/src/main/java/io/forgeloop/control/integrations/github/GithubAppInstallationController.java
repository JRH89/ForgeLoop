package io.forgeloop.control.integrations.github;

import java.net.URI;
import io.forgeloop.control.domain.GithubInstallation;
import io.forgeloop.control.domain.GithubInstallationRepository;
import io.forgeloop.control.security.OperatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts GitHub App installation; repository identity is received from GitHub rather than typed by an operator. */
@RestController
@RequestMapping("/api/github/app")
public class GithubAppInstallationController {
    private final String appSlug;
    private final GithubInstallationState state;
    private final GithubInstallationRepository installations;
    private final GithubInstallationRepositorySyncService synchronization;
    private final OperatorContext operators;

    public GithubAppInstallationController(@Value("${forgeloop.github.app-slug:}") String appSlug, GithubInstallationState state,
                                           GithubInstallationRepository installations, GithubInstallationRepositorySyncService synchronization,
                                           OperatorContext operators) {
        this.appSlug = appSlug;
        this.state = state;
        this.installations = installations;
        this.synchronization = synchronization;
        this.operators = operators;
    }

    @GetMapping("/install")
    public ResponseEntity<Void> install() {
        if (appSlug == null || !appSlug.matches("[A-Za-z0-9-]+")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("https://github.com/apps/" + appSlug + "/installations/new?state=" + state.issue(operators.organizationId())))
                .build();
    }
    /** GitHub redirects here after installation; signed state prevents cross-tenant installation ownership. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("installation_id") long installationId, @RequestParam String state) {
        String organizationId = this.state.verify(state);
        installations.findByInstallationId(installationId).ifPresentOrElse(existing -> {
            if (!existing.getOrganizationId().equals(organizationId)) {
                throw new IllegalArgumentException("GitHub installation is already owned by another organization");
            }
        }, () -> installations.save(new GithubInstallation(installationId, organizationId)));
        // The callback is authoritative for ownership and also closes the race where GitHub sends the
        // installation webhook before this browser redirect reaches ForgeLoop.
        synchronization.synchronizeInstallation(installationId);
        return ResponseEntity.noContent().build();
    }
}
