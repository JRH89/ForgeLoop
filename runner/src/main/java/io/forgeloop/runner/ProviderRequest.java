package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;

/** Runner-local provider input. Neither keys nor raw request content leave the runner as control-plane telemetry. */
public record ProviderRequest(String model, String instructions, String input, int maxOutputTokens, JsonNode outputSchema,
                              @com.fasterxml.jackson.annotation.JsonIgnore SkillSelection skillSelection) {
    public ProviderRequest(String model, String instructions, String input, int maxOutputTokens, JsonNode outputSchema) {
        this(model, instructions, input, maxOutputTokens, outputSchema, SkillSelection.none());
    }

    /** Procedures affect instructions only; metadata is local and excluded from vendor serialization. */
    public ProviderRequest withSkills(SkillSelection selection) {
        if (selection == null) throw new IllegalArgumentException("Skill selection is required");
        if (!selection.enabled()) return this;
        if (skillSelection.enabled()) {
            if (!skillSelection.metadata().equals(selection.metadata()))
                throw new IllegalArgumentException("Conflicting stage skill selections cannot be stacked");
            return this;
        }
        return new ProviderRequest(model, selection.composeInstructions(instructions), input, maxOutputTokens, outputSchema, selection);
    }

    public ProviderRequest {
        if (model == null || model.isBlank() || instructions == null || instructions.isBlank() || input == null || input.isBlank()
                || maxOutputTokens < 128 || maxOutputTokens > 32_768) throw new IllegalArgumentException("Provider request is invalid");
        if (outputSchema != null && !outputSchema.isObject()) throw new IllegalArgumentException("Provider output schema must be a JSON object");
        outputSchema = outputSchema == null ? null : outputSchema.deepCopy();
        skillSelection = skillSelection == null ? SkillSelection.none() : skillSelection;
    }

    public ProviderRequest(String model, String instructions, String input, int maxOutputTokens) {
        this(model, instructions, input, maxOutputTokens, null);
    }

    @Override public JsonNode outputSchema() { return outputSchema == null ? null : outputSchema.deepCopy(); }
}
