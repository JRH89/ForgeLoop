package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.application.FeatureRunService;
import io.forgeloop.control.domain.GithubDeliveryRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.mockito.Mockito;

class GithubWebhookControllerTest {
    private final GithubDeliveryRepository deliveries = Mockito.mock(GithubDeliveryRepository.class);
    private final FeatureRunService runs = Mockito.mock(FeatureRunService.class);
    private final RepositoryConnectionRepository connections = Mockito.mock(RepositoryConnectionRepository.class);
    private final String secret = "webhook-test-secret";
    private final GithubWebhookController controller = new GithubWebhookController(
            new GithubWebhookVerifier(), deliveries, runs, connections, new ObjectMapper(), secret);

    @Test
    void createsRunOnlyForNewSignedLabeledIssue() throws Exception {
        String body = """
                {"action":"opened","repository":{"full_name":"JRH89/Ticketly"},
                 "issue":{"number":42,"title":"Fix login","body":"- Users can sign in","labels":[{"name":"forgeloop"}]}}
                """;
        RepositoryConnection connection = new RepositoryConnection("JRH89/Ticketly", 1, "master", "forgeloop", "GENERIC", List.of("unit"), 20);
        when(deliveries.existsByDeliveryId("delivery-1")).thenReturn(false);
        when(connections.findByRepository("JRH89/Ticketly")).thenReturn(java.util.Optional.of(connection));

        ResponseEntity<Void> response = controller.receive("delivery-1", "issues", signature(body), body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(deliveries).save(any());
        verify(runs).submit(any());
    }

    @Test
    void rejectsUnsignedDeliveryBeforePersistingIt() {
        ResponseEntity<Void> response = controller.receive("delivery-1", "issues", "sha256=bad", "{}");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(deliveries, never()).save(any());
        verify(runs, never()).submit(any());
    }

    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
