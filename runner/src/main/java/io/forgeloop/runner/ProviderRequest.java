package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;

/** Runner-local provider input. Neither keys nor raw request content leave the runner as control-plane telemetry. */
public record ProviderRequest(String model, String instructions, String input, int maxOutputTokens, JsonNode outputSchema) {
    public ProviderRequest {
        if (model == null || model.isBlank() || instructions == null || instructions.isBlank() || input == null || input.isBlank()
                || maxOutputTokens < 128 || maxOutputTokens > 32_768) throw new IllegalArgumentException("Provider request is invalid");
        if (outputSchema != null && !outputSchema.isObject()) throw new IllegalArgumentException("Provider output schema must be a JSON object");
        outputSchema = outputSchema == null ? null : outputSchema.deepCopy();
    }

    public ProviderRequest(String model, String instructions, String input, int maxOutputTokens) {
        this(model, instructions, input, maxOutputTokens, null);
    }

    @Override public JsonNode outputSchema() { return outputSchema == null ? null : outputSchema.deepCopy(); }
}
