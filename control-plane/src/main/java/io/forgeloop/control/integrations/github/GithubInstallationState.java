package io.forgeloop.control.integrations.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Short-lived signed callback state binds a GitHub installation to the initiating tenant and operator.
 * The payload is {@code organization \n subject \n expiry}; neither identifier may contain a newline.
 */
@Component
public class GithubInstallationState {
    private static final long LIFETIME_SECONDS = 600;
    private final String secret;
    public GithubInstallationState(@Value("${forgeloop.github.installation-state-secret:}") String secret) { this.secret = secret; }

    /** The tenant and operator that started an installation, recovered from verified state. */
    public record Claim(String organizationId, String subject) { }

    public String issue(String organizationId, String subject) {
        if (organizationId == null || organizationId.isBlank() || organizationId.contains("\n")
                || subject == null || subject.isBlank() || subject.contains("\n")) {
            throw new IllegalArgumentException("GitHub installation state requires an organization and operator");
        }
        return sign(organizationId + "\n" + subject + "\n" + (Instant.now().getEpochSecond() + LIFETIME_SECONDS));
    }

    public Claim verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", 2);
        if (parts.length != 2 || !MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII), signature(parts[0]).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("GitHub installation state is invalid");
        }
        String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\n", -1);
        if (values.length != 3) throw new IllegalArgumentException("GitHub installation state is invalid");
        long expiresAt;
        try { expiresAt = Long.parseLong(values[2]); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("GitHub installation state is invalid"); }
        if (expiresAt < Instant.now().getEpochSecond()) throw new IllegalArgumentException("GitHub installation state has expired");
        return new Claim(values[0], values[1]);
    }

    private String sign(String value) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + signature(encoded);
    }

    private String signature(String value) {
        try {
            if (secret.length() < 32) throw new IllegalStateException("GitHub installation state secret is required");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub installation state signing failed", exception);
        }
    }
}
