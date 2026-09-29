package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ProviderCostCalculatorTest {
    @Test void usesExplicitModelPolicyPricingAndRoundsSmallCostsUp() {
        var policy = new ProviderExecutionPolicy("anthropic", "configured-model", 2, new java.math.BigDecimal("3"), new java.math.BigDecimal("15"));
        var cost = new ProviderCostCalculator().fromEnvironment(policy, new ProviderResult("ok",500000,250000,"id"));
        assertTrue(cost.known()); assertEquals(5250000,cost.estimatedCostMicros());
        var fractional = new ProviderExecutionPolicy("local", "model", 1, new java.math.BigDecimal("0.1"), new java.math.BigDecimal("0.2"));
        assertEquals(1,new ProviderCostCalculator().fromEnvironment(fractional,new ProviderResult("ok",1,1,"id")).estimatedCostMicros());
    }
    @Test void calculatesMicroDollarCostFromOperatorSuppliedRates() {
        ProviderCostEstimate cost = new ProviderCostCalculator().calculate(new ProviderResult("ok", 500_000, 250_000, "id"), 3_000_000, 15_000_000);
        assertTrue(cost.known());
        assertEquals(5_250_000, cost.estimatedCostMicros());
    }

    @Test void calculatesConversationTurnCostFromPolicyWithoutTextResult() {
        var policy = new ProviderExecutionPolicy("openai", "model", 2, new java.math.BigDecimal("2.5"), new java.math.BigDecimal("10"));
        ProviderCostEstimate cost = new ProviderCostCalculator().fromTokens(policy, 1_000_000, 250_000);
        assertTrue(cost.known());
        assertEquals(5_000_000, cost.estimatedCostMicros());
        assertFalse(new ProviderCostCalculator().fromTokens(new ProviderExecutionPolicy("local", "model", 1), 1, 1).known());
    }
}
