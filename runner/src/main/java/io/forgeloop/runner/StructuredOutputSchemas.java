package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Central JSON Schemas shared by worker prompts and provider adapters. */
public final class StructuredOutputSchemas {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode PLAN = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{
               "acceptanceCriteria":{"type":"array","items":{"type":"string"}},
               "tasks":{"type":"array","items":{"type":"object","additionalProperties":false,
                 "properties":{
                   "key":{"type":"string"},"role":{"type":"string","enum":["IMPLEMENTATION","BACKEND","FRONTEND","INDEPENDENT_TEST","INTEGRATION"]},
                   "title":{"type":"string"},"requiredCapability":{"type":"string","enum":["provider","git"]},
                   "dependencies":{"type":"array","items":{"type":"string"}},"ownedPaths":{"type":"array","items":{"type":"string"}},
                   "attemptBudget":{"type":"integer"},"budgetMicros":{"type":"integer"}},
                 "required":["key","role","title","requiredCapability","dependencies","ownedPaths","attemptBudget","budgetMicros"]}}},
             "required":["acceptanceCriteria","tasks"]}
            """);
    private static final JsonNode PATCH = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{"summary":{"type":"string"},"changes":{"type":"array","items":{"type":"object","additionalProperties":false,
               "properties":{"path":{"type":"string"},"content":{"type":"string"},"message":{"type":"string"}},
               "required":["path","content","message"]}}},
             "required":["summary","changes"]}
            """);
    private static final JsonNode REVIEW = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{"approved":{"type":"boolean"},"summary":{"type":"string"},"criteria":{"type":"array","items":{"type":"object","additionalProperties":false,
               "properties":{"statement":{"type":"string"},"status":{"type":"string","enum":["PASS","FAIL"]},"evidence":{"type":"string"}},
               "required":["statement","status","evidence"]}}},
             "required":["approved","summary","criteria"]}
            """);

    private StructuredOutputSchemas() { }
    public static JsonNode plan() { return PLAN.deepCopy(); }
    public static JsonNode patch() { return PATCH.deepCopy(); }
    public static JsonNode review() { return REVIEW.deepCopy(); }
    private static JsonNode parse(String schema) {
        try { return JSON.readTree(schema); }
        catch (Exception invalid) { throw new ExceptionInInitializerError(invalid); }
    }
}
