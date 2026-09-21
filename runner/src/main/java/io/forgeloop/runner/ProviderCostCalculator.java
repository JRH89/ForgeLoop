package io.forgeloop.runner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Calculates costs from operator-managed model rates; ForgeLoop does not hard-code changeable vendor prices. */
public final class ProviderCostCalculator {
    public ProviderCostEstimate fromEnvironment(ProviderExecutionPolicy policy, ProviderResult result) {
        String prefix = "FORGELOOP_" + normalize(policy.provider()) + "_" + normalize(policy.model());
        String input = System.getenv(prefix + "_INPUT_MICROS_PER_MILLION");
        String output = System.getenv(prefix + "_OUTPUT_MICROS_PER_MILLION");
        if (input == null || input.isBlank() || output == null || output.isBlank()) return ProviderCostEstimate.unknown();
        return calculate(result, Long.parseLong(input), Long.parseLong(output));
    }

    ProviderCostEstimate calculate(ProviderResult result, long inputMicrosPerMillion, long outputMicrosPerMillion) {
        if (inputMicrosPerMillion < 0 || outputMicrosPerMillion < 0) throw new IllegalArgumentException("Provider pricing cannot be negative");
        BigDecimal micros = BigDecimal.valueOf(result.inputTokens()).multiply(BigDecimal.valueOf(inputMicrosPerMillion))
                .add(BigDecimal.valueOf(result.outputTokens()).multiply(BigDecimal.valueOf(outputMicrosPerMillion)))
                .divide(BigDecimal.valueOf(1_000_000), 0, RoundingMode.CEILING);
        return new ProviderCostEstimate(micros.longValueExact(), true);
    }

    private static String normalize(String value) { return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_"); }
}
