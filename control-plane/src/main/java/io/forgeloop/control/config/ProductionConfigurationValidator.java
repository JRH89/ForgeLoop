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
    private final String webhookSecret;
    private final String datasourceUrl;
    private final String ddlAuto;

    public ProductionConfigurationValidator(@Value("${forgeloop.security.mode:production}") String mode,
                                            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                                            @Value("${forgeloop.github.webhook-secret:}") String webhookSecret,
                                            @Value("${spring.datasource.url:}") String datasourceUrl,
                                            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddlAuto) {
        this.mode = mode; this.issuerUri = issuerUri; this.webhookSecret = webhookSecret; this.datasourceUrl = datasourceUrl; this.ddlAuto = ddlAuto;
    }
    @Override public void afterSingletonsInstantiated() { if ("production".equals(mode)) validate(); }
    void validate() {
        List<String> missing = new java.util.ArrayList<>();
        if (issuerUri.isBlank()) missing.add("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI");
        if (webhookSecret.isBlank()) missing.add("FORGELOOP_GITHUB_WEBHOOK_SECRET");
        if (datasourceUrl.isBlank() || datasourceUrl.startsWith("jdbc:h2:")) missing.add("production PostgreSQL datasource");
        if (!"validate".equals(ddlAuto)) missing.add("SPRING_JPA_DDL_AUTO=validate");
        if (!missing.isEmpty()) throw new IllegalStateException("Production configuration is incomplete: " + String.join(", ", missing));
    }
}
