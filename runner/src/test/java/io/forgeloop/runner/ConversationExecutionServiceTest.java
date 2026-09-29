package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConversationExecutionServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ConversationRequest request = new ConversationRequest("model", "instructions", List.of(new UserText("hello")), List.of(), 128, Duration.ofSeconds(5));

    @Test void retriesTransientFailuresAndReturnsExactAttemptCount() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ConversationExecution result = new ConversationExecutionService().converse(new FakeClient(() -> {
            if (attempts.incrementAndGet() < 3) throw new ProviderException("temporary", true);
            return turn();
        }), request, 3);
        assertEquals(3, result.attemptCount());
        assertEquals(StopReason.END_TURN, result.turn().stopReason());
    }

    @Test void permanentFailuresAndRetryLimitAreHonored() {
        AtomicInteger attempts = new AtomicInteger();
        ProviderExecutionFailure failure = assertThrows(ProviderExecutionFailure.class, () -> new ConversationExecutionService().converse(
                new FakeClient(() -> { attempts.incrementAndGet(); throw new ProviderException("permanent", false); }), request, 3));
        assertEquals(1, failure.attemptCount());
        assertEquals(1, attempts.get());
        assertThrows(IllegalArgumentException.class, () -> new ConversationExecutionService().converse(new FakeClient(() -> turn()), request, 4));
    }

    private static ConversationTurn turn() {
        return new ConversationTurn("done", List.of(), StopReason.END_TURN, 2, 3, "req", JSON.createArrayNode(), "{}");
    }

    @FunctionalInterface private interface Action { ConversationTurn run() throws ProviderException; }
    private record FakeClient(Action action) implements ConversationClient {
        @Override public String serialize(ConversationRequest ignored) { return "{}"; }
        @Override public ConversationTurn converse(ConversationRequest ignored) throws ProviderException { return action.run(); }
    }
}
