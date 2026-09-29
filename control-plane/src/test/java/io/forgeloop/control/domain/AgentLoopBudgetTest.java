package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AgentLoopBudgetTest {
    @Test
    void acceptsBothAllowedEndpointsAndCopiesValidatedValues() {
        AgentLoopBudget budget = new AgentLoopBudget(1, 10_000, 60, 65_536);
        AgentLoopBudget copy = budget.copy();
        assertEquals(1, copy.getMaxToolCalls());
        assertEquals(10_000, copy.getMaxTokens());
        assertEquals(60, copy.getMaxWallSeconds());
        assertEquals(65_536, copy.getMaxConversationBytes());
        assertEquals(1000, new AgentLoopBudget(1000, 100_000_000, 14_400, 4_194_304).getMaxToolCalls());
    }

    @Test
    void rejectsValuesOutsideAnyPolicyBound() {
        assertThrows(IllegalArgumentException.class, () -> new AgentLoopBudget(0, 10_000, 60, 65_536));
        assertThrows(IllegalArgumentException.class, () -> new AgentLoopBudget(1, 9_999, 60, 65_536));
        assertThrows(IllegalArgumentException.class, () -> new AgentLoopBudget(1, 10_000, 59, 65_536));
        assertThrows(IllegalArgumentException.class, () -> new AgentLoopBudget(1, 10_000, 60, 65_535));
    }
}
