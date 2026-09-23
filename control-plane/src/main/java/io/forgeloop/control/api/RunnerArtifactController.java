package io.forgeloop.control.api;

import io.forgeloop.control.application.ArtifactUploadService;
import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.domain.ArtifactMetadata;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Binary runner endpoint avoids base64 inflation while retaining lease-bound authentication. */
@RestController
@RequestMapping("/api/runner/artifacts")
public class RunnerArtifactController {
    private final RunnerService runners; private final ArtifactUploadService artifacts;
    public RunnerArtifactController(RunnerService runners, ArtifactUploadService artifacts) { this.runners=runners;this.artifacts=artifacts; }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ArtifactReceipt upload(@RequestHeader("X-ForgeLoop-Runner-Id") String runnerId,
                                  @RequestHeader("X-ForgeLoop-Runner-Credential") String credential,
                                  @RequestHeader("X-ForgeLoop-Lease-Id") String leaseId,
                                  @RequestHeader("X-ForgeLoop-Lease-Nonce") String nonce,
                                  @RequestHeader("X-ForgeLoop-Artifact-Sha256") String sha256,
                                  @RequestBody byte[] content) {
        runners.authenticated(runnerId, credential);
        ArtifactMetadata stored = artifacts.upload(leaseId, runnerId, nonce, MediaType.APPLICATION_JSON_VALUE, sha256, content);
        return new ArtifactReceipt(stored.getStorageReference(), stored.getSha256(), stored.getSizeBytes(), stored.getRetainUntil());
    }
    public record ArtifactReceipt(String reference, String sha256, long sizeBytes, String retainUntil) { }
}
