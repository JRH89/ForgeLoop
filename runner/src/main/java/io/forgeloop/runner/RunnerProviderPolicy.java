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
    private final Map<String, ProviderExecutionPolicy> roles;
    private RunnerProviderPolicy(Map<String, ProviderExecutionPolicy> roles) { this.roles = Map.copyOf(roles); }

    public static RunnerProviderPolicy load(Path path) throws IOException {
        JsonNode root = JSON.readTree(path.toFile());
        if (!root.isObject() || root.isEmpty()) throw new IllegalArgumentException("Provider policy must define at least one role");
        Map<String, ProviderExecutionPolicy> roles = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next(); JsonNode value = field.getValue();
            if (!field.getKey().matches("[A-Z_]+|default") || !value.isObject() || value.size() != 3
                    || !value.hasNonNull("provider") || !value.hasNonNull("model") || !value.hasNonNull("maxAttempts")) {
                throw new IllegalArgumentException("Provider policy entry is invalid");
            }
            roles.put(field.getKey(), new ProviderExecutionPolicy(value.path("provider").asText(), value.path("model").asText(), value.path("maxAttempts").asInt()));
        }
        return new RunnerProviderPolicy(roles);
    }

    public ProviderExecutionPolicy select(String role) {
        WorkerRolePolicy.require(role);
        ProviderExecutionPolicy selected = roles.get(role);
        if (selected == null) selected = roles.get("default");
        if (selected == null) throw new IllegalArgumentException("No provider policy exists for task role " + role);
        return selected;
    }
}
