package io.forgeloop.control.integrations.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Short-lived signed callback state binds a GitHub installation to the initiating tenant. */
@Component
public class GithubInstallationState {
    private final String secret;
    public GithubInstallationState(@Value("${forgeloop.github.installation-state-secret:}") String secret) { this.secret = secret; }
    public String issue(String organizationId) { return sign(organizationId + ":" + (Instant.now().getEpochSecond() + 600)); }
    public String verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", 2); if (parts.length != 2 || !MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII), signature(parts[0]).getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("GitHub installation state is invalid");
        String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", 2); if (values.length != 2 || Long.parseLong(values[1]) < Instant.now().getEpochSecond()) throw new IllegalArgumentException("GitHub installation state has expired"); return values[0];
    }
    private String sign(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "." + signature(Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))); }
    private String signature(String value) { try { if (secret.length() < 32) throw new IllegalStateException("GitHub installation state secret is required"); Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII))); } catch (Exception exception) { throw new IllegalStateException("GitHub installation state signing failed", exception); } }
}
