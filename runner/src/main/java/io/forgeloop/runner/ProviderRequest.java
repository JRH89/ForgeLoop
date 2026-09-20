package io.forgeloop.runner;

/** Runner-local provider input. Neither keys nor raw request content leave the runner as control-plane telemetry. */
public record ProviderRequest(String model, String instructions, String input, int maxOutputTokens) {
    public ProviderRequest {
        if (model == null || model.isBlank() || instructions == null || instructions.isBlank() || input == null || input.isBlank()
                || maxOutputTokens < 128 || maxOutputTokens > 32_768) throw new IllegalArgumentException("Provider request is invalid");
    }
}
