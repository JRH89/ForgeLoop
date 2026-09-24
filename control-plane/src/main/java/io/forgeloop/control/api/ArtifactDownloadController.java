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
    private final ArtifactDownloadService artifacts;
    public ArtifactDownloadController(ArtifactDownloadService artifacts) { this.artifacts = artifacts; }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        ArtifactDownloadService.Download artifact = artifacts.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(artifact.displayName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(artifact.content());
    }
}
