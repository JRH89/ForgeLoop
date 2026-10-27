package io.forgeloop.control.api;

import io.forgeloop.control.application.ArtifactDownloadService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Streams tenant-authorized evidence with an inline, safe filename. */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactDownloadController {
    /**
     * Artifact bytes are produced by runners, not by ForgeLoop, yet they are served inline on the
     * operator origin. A sandboxed, resource-free policy guarantees that even a mislabeled payload
     * can never execute script with the operator's session.
     */
    static final String CONTENT_SECURITY_POLICY = "sandbox; default-src 'none'";
    private final ArtifactDownloadService artifacts;
    public ArtifactDownloadController(ArtifactDownloadService artifacts) { this.artifacts = artifacts; }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        ArtifactDownloadService.Download artifact = artifacts.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(artifact.displayName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .body(artifact.content());
    }
}
