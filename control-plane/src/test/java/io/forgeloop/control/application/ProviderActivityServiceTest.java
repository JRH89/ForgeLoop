package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.ProviderActivity;
import io.forgeloop.control.domain.ProviderActivityRepository;
import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProviderActivityServiceTest {
    private final ProviderActivityRepository activities = Mockito.mock(ProviderActivityRepository.class);
    private final OperatorContext operators = Mockito.mock(OperatorContext.class);
    private final ProviderActivityService service = new ProviderActivityService(activities, operators);

    @Test void historyUsesTheSelectedOrganizationAndValidatedPeriod() {
        when(operators.organizationId()).thenReturn("org-1");

        service.list(30, "acme/app");

        verify(activities).findUsage(Mockito.eq("org-1"), any(Instant.class), Mockito.eq("acme/app"), any());
        assertThrows(IllegalArgumentException.class, () -> service.list(8, "acme/app"));
    }

    @Test void activityCreationIsIdempotentAndStoresMetadataOnly() {
        when(activities.save(any(ProviderActivity.class))).thenAnswer(call -> call.getArgument(0));

        ProviderActivity created = service.record("org-1", "AI_CHAT", "acme/app", "chat-1", "openai", "gpt-test",
                100, 20, 50, true);

        assertEquals("org-1", created.getOrganizationId());
        assertEquals("AI_CHAT", created.getActivityType());
        assertEquals(120, created.getInputTokens() + created.getOutputTokens());
        verify(activities).findByOrganizationIdAndActivityTypeAndSourceId("org-1", "AI_CHAT", "chat-1");
        verify(activities).save(any(ProviderActivity.class));
    }

    @Test void existingActivityIsNotCountedTwiceAndUnknownPriceIsNotZeroDollarKnown() {
        ProviderActivity existing = new ProviderActivity("org-1", "acme/app", "REPOSITORY_SCAN", "scan-1",
                "anthropic", "test-model", 4, 5, 0, false, Instant.now());
        when(activities.findByOrganizationIdAndActivityTypeAndSourceId("org-1", "REPOSITORY_SCAN", "scan-1"))
                .thenReturn(Optional.of(existing));

        ProviderActivity returned = service.record("org-1", "REPOSITORY_SCAN", "acme/app", "scan-1", "anthropic",
                "test-model", 4, 5, 0, false);

        assertSame(existing, returned);
        assertEquals(false, returned.isCostKnown());
        verify(activities, never()).save(any(ProviderActivity.class));
    }

    @Test void rejectsUnknownTypesAndContradictoryCostMetadata() {
        assertThrows(IllegalArgumentException.class, () -> service.record("org-1", "RAW_PROMPT", "acme/app", "x", "openai", "gpt", 1, 1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> service.record("org-1", "AI_CHAT", "acme/app", "x", "openai", "gpt", 1, 1, 1, false));
        verify(activities, never()).save(any(ProviderActivity.class));
    }
}
