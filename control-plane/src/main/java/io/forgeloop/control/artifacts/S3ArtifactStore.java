package io.forgeloop.control.artifacts;

import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible immutable backend with checksum metadata and full read-after-write verification. */
final class S3ArtifactStore implements ArtifactStore {
    private final S3Client client;
    private final String bucket;
    private final String prefix;
    S3ArtifactStore(S3Client client, String bucket, String prefix) { this.client = client; this.bucket = bucket; this.prefix = prefix; }

    @Override public StoredObject putVerified(String key, byte[] content, String contentType, String sha256) {
        String objectKey = prefix.isBlank() ? key : prefix + "/" + key;
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType)
                    .ifNoneMatch("*").metadata(Map.of("forgeloop-sha256", sha256)).build(), RequestBody.fromBytes(content));
        } catch (S3Exception existing) {
            // A retry after a database failure or a racing worker may find the immutable key first.
            // Only identical persisted bytes are accepted; all other precondition failures remain fatal.
            if (existing.statusCode() != 412) throw existing;
        }
        byte[] persisted = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).asByteArray();
        String actual = ArtifactDigests.sha256(persisted);
        if (persisted.length != content.length || !actual.equals(sha256)) throw new IllegalStateException("Immutable S3 artifact key contains different or corrupt content");
        return new StoredObject(persisted.length, actual);
    }
}
