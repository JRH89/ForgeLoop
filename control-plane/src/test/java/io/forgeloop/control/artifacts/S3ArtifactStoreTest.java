package io.forgeloop.control.artifacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ArtifactStoreTest {
    @Test void createsAnImmutableObjectAndVerifiesThePersistedBytes() {
        S3Client client = mock(S3Client.class);
        byte[] content = "{\"passed\":true}".getBytes(StandardCharsets.UTF_8);
        String digest = ArtifactDigests.sha256(content);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        ArtifactStore.StoredObject result = new S3ArtifactStore(client, "evidence", "prod")
                .putVerified("org/run/lease.json", content, "application/json", digest);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("*", request.getValue().ifNoneMatch());
        assertEquals("prod/org/run/lease.json", request.getValue().key());
        assertEquals(digest, result.sha256());
    }
}
