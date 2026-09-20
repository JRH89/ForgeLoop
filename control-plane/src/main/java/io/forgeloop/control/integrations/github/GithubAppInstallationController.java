package io.forgeloop.control.integrations.github;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starts GitHub App installation; repository identity is received from GitHub rather than typed by an operator. */
@RestController
@RequestMapping("/api/github/app")
public class GithubAppInstallationController {
    private final String appSlug;

    public GithubAppInstallationController(@Value("${forgeloop.github.app-slug:}") String appSlug) { this.appSlug = appSlug; }

    @GetMapping("/install")
    public ResponseEntity<Void> install() {
        if (appSlug == null || !appSlug.matches("[A-Za-z0-9-]+")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("https://github.com/apps/" + appSlug + "/installations/new"))
                .build();
    }
}
