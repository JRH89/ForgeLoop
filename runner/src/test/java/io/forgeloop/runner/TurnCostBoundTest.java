package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TurnCostBoundTest {
    private final ProviderCostCalculator costs = new ProviderCostCalculator();
    private final ProviderExecutionPolicy priced = new ProviderExecutionPolicy("openai", "model", 1,
            new BigDecimal("1"), new BigDecimal("0.0000001"));

    @Test
    void firstTurnUsesUtf8BytesAndRoundsTheMaximumCostUp() {
        TurnCostBound bound = TurnCostBound.calculate("é🙂", 0, 0, 128, priced, costs);

        assertEquals(6, bound.inputTokenUpperBound());
        assertEquals(128, bound.outputTokenUpperBound());
        assertEquals(7, bound.reservedMicros());
        assertEquals(6, bound.serializedRequestBytes());
        assertEquals(true, bound.costKnown());
    }

    @Test
    void laterTurnAddsOnlySerializedGrowthToLastReportedInputTokens() {
        TurnCostBound grown = TurnCostBound.calculate("é🙂abcdef", 7, 10, 128, priced, costs);
        TurnCostBound shrunk = TurnCostBound.calculate("x", 7, 10, 128, priced, costs);

        assertEquals(15, grown.inputTokenUpperBound());
        assertEquals(10, shrunk.inputTokenUpperBound());
    }
}
