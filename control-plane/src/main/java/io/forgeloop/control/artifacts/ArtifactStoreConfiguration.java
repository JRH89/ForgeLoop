package io.forgeloop.control.artifacts;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
class ArtifactStoreConfiguration {
    @Bean ArtifactStore artifactStore(@Value("${forgeloop.artifacts.storage-uri:}") String storageUri,
                                      @Value("${forgeloop.artifacts.s3-endpoint:}") String endpoint,
                                      @Value("${forgeloop.artifacts.s3-region:us-east-1}") String region) {
        if (storageUri == null || storageUri.isBlank()) return new FileSystemArtifactStore(Path.of(System.getProperty("java.io.tmpdir"), "forgeloop-artifacts"));
        URI uri = URI.create(storageUri);
        if ("file".equals(uri.getScheme())) return new FileSystemArtifactStore(Path.of(uri));
        if (!"s3".equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("Artifact storage currently supports file: and s3: URIs");
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint)).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        return new S3ArtifactStore(builder.build(), uri.getHost(), uri.getPath().replaceAll("^/|/$", ""));
    }
}
