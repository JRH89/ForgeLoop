package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Small strict JSON argument reader; unknown and missing fields fail closed at the gateway. */
final class ToolArguments {
    private ToolArguments() { }
    static void fields(JsonNode args, Set<String> allowed, String... required) throws LoopToolFailure {
        if (args == null || !args.isObject()) throw invalid("arguments");
        var names = args.fieldNames();
        while (names.hasNext()) if (!allowed.contains(names.next())) throw invalid("arguments");
        for (String field : required) if (!args.has(field) || args.get(field).isNull()) throw invalid(field);
    }
    static String string(JsonNode args, String field, boolean allowEmpty) throws LoopToolFailure {
        JsonNode value = args.get(field);
        if (value == null || !value.isTextual() || (!allowEmpty && value.asText().isBlank())) throw invalid(field);
        return value.asText();
    }
    static int integer(JsonNode args, String field, int min, int max) throws LoopToolFailure {
        JsonNode value = args.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) throw invalid(field);
        return value.asInt();
    }
    static boolean bool(JsonNode args, String field) throws LoopToolFailure {
        JsonNode value = args.get(field);
        if (value == null || !value.isBoolean()) throw invalid(field);
        return value.asBoolean();
    }
    static LoopToolFailure invalid(String field) { return new LoopToolFailure(FailureCategory.VALIDATION, "Invalid or missing field: " + field); }
}
