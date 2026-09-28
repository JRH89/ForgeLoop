package io.forgeloop.control.support;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Optional SMTP adapter; recovery is unavailable unless explicitly enabled and fully configured. */
@Component
public class SmtpSupportRecoveryDelivery implements SupportRecoveryDelivery {
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String mailHost;
    private final String from;
    private final String publicBaseUrl;

    public SmtpSupportRecoveryDelivery(ObjectProvider<JavaMailSender> sender,
            @Value("${forgeloop.support.recovery.enabled:false}") boolean enabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${forgeloop.support.recovery.from:}") String from,
            @Value("${forgeloop.support.recovery.public-base-url:}") String publicBaseUrl) {
        this.sender = sender;
        this.enabled = enabled;
        this.mailHost = mailHost;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override public boolean available() {
        return enabled && !mailHost.isBlank() && !from.isBlank() && validBaseUrl(publicBaseUrl)
                && sender.getIfAvailable() != null;
    }

    @Override public void sendRecovery(String recipient, String token) {
        if (!available()) throw new IllegalStateException("Support email recovery is not configured");
        String link = publicBaseUrl.replaceAll("/+$", "") + "/support#recover=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("ForgeLoop support access recovery");
        message.setText("A request was made to recover access to a ForgeLoop support ticket.\n\n"
                + "Open this link within 30 minutes to verify this email address and receive a replacement private link:\n"
                + link + "\n\nIf you did not request this, you can ignore this message. No ticket details are included here.");
        sender.getObject().send(message);
    }

    private static boolean validBaseUrl(String value) {
        try {
            URI uri = URI.create(value.strip());
            boolean localHttp = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            boolean https = "https".equals(uri.getScheme());
            return (https || localHttp) && uri.getHost() != null && uri.getUserInfo() == null
                    && (uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
