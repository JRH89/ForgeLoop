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
    private static final JsonNode REPOSITORY_SCAN = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{"findings":{"type":"array","maxItems":12,"items":{"type":"object","additionalProperties":false,
               "properties":{"severity":{"type":"string","enum":["CRITICAL","HIGH","MEDIUM","LOW"]},
                 "title":{"type":"string"},"description":{"type":"string"},"impact":{"type":"string"},
                 "evidence":{"type":"string"},"affectedFiles":{"type":"array","maxItems":10,"items":{"type":"string"}},
                 "acceptanceCriteria":{"type":"array","maxItems":6,"items":{"type":"string"}}},
               "required":["severity","title","description","impact","evidence","affectedFiles","acceptanceCriteria"]}}},
            "required":["findings"]}
            """);
    private static final JsonNode REPOSITORY_ISSUE_PROPOSAL = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{"title":{"type":"string","maxLength":200},"body":{"type":"string","maxLength":12000},
               "acceptanceCriteria":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"string","maxLength":400}}},
            "required":["title","body","acceptanceCriteria"]}
            """);
    private static final JsonNode ISSUE_CHAT = parse("""
            {"type":"object","additionalProperties":false,
             "properties":{"assistantMessage":{"type":"string","maxLength":2000},
               "title":{"type":"string","maxLength":200},"body":{"type":"string","maxLength":12000},
               "acceptanceCriteria":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"string","maxLength":400}}},
             "required":["assistantMessage","title","body","acceptanceCriteria"]}
            """);

    private StructuredOutputSchemas() { }
    public static JsonNode plan() { return PLAN.deepCopy(); }
    public static JsonNode patch() { return PATCH.deepCopy(); }
    public static JsonNode review() { return REVIEW.deepCopy(); }
    /** Bind output to canonical server criteria instead of relying on prose-copying precision. */
    public static JsonNode review(java.util.List<String> statements) {
        if (statements == null || statements.isEmpty()
                || statements.stream().anyMatch(value -> value == null || value.isBlank())
                || statements.stream().distinct().count() != statements.size()) {
            throw new IllegalArgumentException("Review criteria must be nonempty and unique");
        }
        com.fasterxml.jackson.databind.node.ObjectNode schema = REVIEW.deepCopy();
        com.fasterxml.jackson.databind.node.ObjectNode criteria =
                (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties").path("criteria");
        criteria.put("minItems", statements.size());
        criteria.put("maxItems", statements.size());
        com.fasterxml.jackson.databind.node.ObjectNode statement =
                (com.fasterxml.jackson.databind.node.ObjectNode) criteria.path("items").path("properties").path("statement");
        statement.set("enum", JSON.valueToTree(statements));
        return schema;
    }
    public static JsonNode repositoryScan() { return REPOSITORY_SCAN.deepCopy(); }
    public static JsonNode repositoryIssueProposal() { return REPOSITORY_ISSUE_PROPOSAL.deepCopy(); }
    public static JsonNode issueChat() { return ISSUE_CHAT.deepCopy(); }
    private static JsonNode parse(String schema) {
        try { return JSON.readTree(schema); }
        catch (Exception invalid) { throw new ExceptionInInitializerError(invalid); }
    }
}
