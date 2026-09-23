package io.forgeloop.control.config;

import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fails startup before traffic is accepted when a production control plane has unsafe or incomplete configuration. */
@Component
public class ProductionConfigurationValidator implements SmartInitializingSingleton {
    private final String mode;
    private final String issuerUri;
    private final String audience;
    private final String webhookSecret;
    private final String datasourceUrl;
    private final String ddlAuto;
    private final String artifactStorageUri;
    private final String encryptionKey;
    private final String githubAppId;
    private final String githubPrivateKey;
    private final String installationStateSecret;

    public ProductionConfigurationValidator(@Value("${forgeloop.security.mode:production}") String mode,
                                            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                                            @Value("${forgeloop.security.oidc-audience:}") String audience,
                                            @Value("${forgeloop.github.webhook-secret:}") String webhookSecret,
                                            @Value("${spring.datasource.url:}") String datasourceUrl,
                                            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddlAuto,
                                            @Value("${forgeloop.artifacts.storage-uri:}") String artifactStorageUri,
                                            @Value("${forgeloop.security.encryption-key:}") String encryptionKey,
                                            @Value("${forgeloop.github.app-id:}") String githubAppId,
                                            @Value("${forgeloop.github.private-key:}") String githubPrivateKey,
                                            @Value("${forgeloop.github.installation-state-secret:}") String installationStateSecret) {
        this.mode = mode; this.issuerUri = issuerUri; this.audience = audience; this.webhookSecret = webhookSecret; this.datasourceUrl = datasourceUrl; this.ddlAuto = ddlAuto; this.artifactStorageUri = artifactStorageUri; this.encryptionKey = encryptionKey; this.githubAppId = githubAppId; this.githubPrivateKey = githubPrivateKey; this.installationStateSecret = installationStateSecret;
    }
    @Override public void afterSingletonsInstantiated() { if ("production".equals(mode)) validate(); }
    void validate() {
        List<String> missing = new java.util.ArrayList<>();
        if (issuerUri.isBlank()) missing.add("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI");
        if (audience.isBlank()) missing.add("FORGELOOP_OIDC_AUDIENCE");
        if (webhookSecret.isBlank()) missing.add("FORGELOOP_GITHUB_WEBHOOK_SECRET");
        if (datasourceUrl.isBlank() || datasourceUrl.startsWith("jdbc:h2:")) missing.add("production PostgreSQL datasource");
        if (!"validate".equals(ddlAuto)) missing.add("SPRING_JPA_DDL_AUTO=validate");
        if (artifactStorageUri.isBlank() || !artifactStorageUri.startsWith("s3://")) missing.add("FORGELOOP_ARTIFACT_STORAGE_URI (s3:// bucket required)");
        if (encryptionKey.length() < 32) missing.add("FORGELOOP_ENCRYPTION_KEY (minimum 32 characters)");
        if (githubAppId.isBlank() || githubPrivateKey.isBlank()) missing.add("GitHub App ID and private key");
        if (installationStateSecret.length() < 32) missing.add("FORGELOOP_GITHUB_INSTALLATION_STATE_SECRET (minimum 32 characters)");
        if (!missing.isEmpty()) throw new IllegalStateException("Production configuration is incomplete: " + String.join(", ", missing));
    }
}
