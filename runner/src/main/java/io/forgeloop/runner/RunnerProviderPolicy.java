package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Runner-local deterministic role-to-provider policy; it contains no credentials. */
public final class RunnerProviderPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final java.util.Set<String> ALLOWED_FIELDS = java.util.Set.of(
            "provider", "model", "maxAttempts", "inputUsdPerMillion", "outputUsdPerMillion", "toolCalling");
    private final Map<String, ProviderExecutionPolicy> roles;
    private final Map<String, Boolean> toolCalling;
    private RunnerProviderPolicy(Map<String, ProviderExecutionPolicy> roles, Map<String, Boolean> toolCalling) {
        this.roles = Map.copyOf(roles); this.toolCalling = Map.copyOf(toolCalling);
    }

    public static RunnerProviderPolicy load(Path path) throws IOException {
        JsonNode root = JSON.readTree(path.toFile());
        if (!root.isObject() || root.isEmpty()) throw new IllegalArgumentException("Provider policy must define at least one role");
        Map<String, ProviderExecutionPolicy> roles = new HashMap<>();
        Map<String, Boolean> toolCalling = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next(); JsonNode value = field.getValue();
            if (!field.getKey().matches("[A-Z_]+|default") || !value.isObject() || (value.size() < 3 || value.size() > 6)
                    || !value.hasNonNull("provider") || !value.hasNonNull("model") || !value.hasNonNull("maxAttempts")) {
                throw new IllegalArgumentException("Provider policy entry is invalid");
            }
            value.fieldNames().forEachRemaining(name -> {
                if (!ALLOWED_FIELDS.contains(name)) throw new IllegalArgumentException("Provider policy entry is invalid");
            });
            boolean hasInputPrice = value.has("inputUsdPerMillion"), hasOutputPrice = value.has("outputUsdPerMillion");
            if (hasInputPrice != hasOutputPrice || value.has("toolCalling") && !value.path("toolCalling").isBoolean())
                throw new IllegalArgumentException("Provider policy entry is invalid");
            java.math.BigDecimal input = null, output = null;
            if (hasInputPrice) {
                if (!value.path("inputUsdPerMillion").isNumber() || !value.path("outputUsdPerMillion").isNumber())
                    throw new IllegalArgumentException("Pricing must specify numeric USD per million tokens for input and output");
                input = value.path("inputUsdPerMillion").decimalValue();
                output = value.path("outputUsdPerMillion").decimalValue();
            }
            roles.put(field.getKey(), new ProviderExecutionPolicy(value.path("provider").asText(), value.path("model").asText(), value.path("maxAttempts").asInt(), input, output));
            if (value.has("toolCalling")) toolCalling.put(field.getKey(), value.path("toolCalling").asBoolean());
        }
        return new RunnerProviderPolicy(roles, toolCalling);
    }

    public ProviderExecutionPolicy select(String role) {
        WorkerRolePolicy.require(role);
        ProviderExecutionPolicy selected = roles.get(role);
        if (selected == null) selected = roles.get("default");
        if (selected == null) throw new IllegalArgumentException("No provider policy exists for task role " + role);
        return selected;
    }

    /** Defaults hosted vendors to tool calling; local OpenAI-compatible servers opt in explicitly. */
    public boolean toolCalling(String role) {
        ProviderExecutionPolicy selected = select(role);
        Boolean configured = toolCalling.get(roles.containsKey(role) ? role : "default");
        return configured == null ? !"local".equals(selected.provider()) : configured;
    }
}
