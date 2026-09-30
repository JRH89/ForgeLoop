package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;

/**
 * Adapts the shared JSON Schemas to Anthropic's supported structured-output subset.
 * Workers still enforce removed output bounds after parsing, before accepting results.
 */
final class AnthropicSchemaCompatibility {
    private static final List<String> UNSUPPORTED_BOUNDS = List.of(
            "maxItems", "minLength", "maxLength", "minimum", "maximum", "multipleOf");

    private AnthropicSchemaCompatibility() { }

    static JsonNode normalize(JsonNode schema) {
        if (schema == null) return null;
        JsonNode normalized = schema.deepCopy();
        removeUnsupportedBounds(normalized);
        return normalized;
    }

    private static void removeUnsupportedBounds(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove(UNSUPPORTED_BOUNDS);
            normalizeMinItems(object);
            Iterator<JsonNode> children = object.elements();
            while (children.hasNext()) removeUnsupportedBounds(children.next());
        } else if (node.isArray()) {
            for (JsonNode child : node) removeUnsupportedBounds(child);
        }
    }

    private static void normalizeMinItems(ObjectNode object) {
        JsonNode minItems = object.get("minItems");
        if (minItems != null && (!minItems.canConvertToInt() || minItems.intValue() < 0 || minItems.intValue() > 1)) {
            object.remove("minItems");
        }
    }
}
