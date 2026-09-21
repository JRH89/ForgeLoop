package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ProviderCostCalculatorTest {
    @Test void calculatesMicroDollarCostFromOperatorSuppliedRates() {
        ProviderCostEstimate cost = new ProviderCostCalculator().calculate(new ProviderResult("ok", 500_000, 250_000, "id"), 3_000_000, 15_000_000);
        assertTrue(cost.known());
        assertEquals(5_250_000, cost.estimatedCostMicros());
    }
}
