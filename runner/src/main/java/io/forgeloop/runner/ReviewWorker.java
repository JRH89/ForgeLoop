package io.forgeloop.runner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-only provider worker that evaluates the integrated diff against the original specification. */
public final class ReviewWorker {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public ReviewResult execute(ProviderExecutionPolicy policy,ProviderClient provider,RunnerTask task,String diff,String correlationId)throws Exception{
        String instructions="Act as an independent software reviewer. Return JSON only with exactly these fields: {approved:boolean,summary:string,criteria:[{statement:string,status:'PASS'|'FAIL',evidence:string}]}. Assess every supplied criterion exactly once using the exact statement. Approve only when every criterion passes and the integrated diff has no obvious security or correctness defect.";
        String criteria=task.acceptanceCriteria().stream().map(value->"- "+value).reduce((a,b)->a+"\n"+b).orElse("- No criteria supplied");
        ProviderExecutionResult execution=new ProviderExecutionService().executeDetailed(provider,new ProviderRequest(policy.model(),instructions,"Specification:\n"+task.specification()+"\n\nAcceptance criteria:\n"+criteria+"\n\nIntegrated diff:\n"+diff,8192,StructuredOutputSchemas.review()),policy.maxAttempts());
        ProviderUsageEvidence usage=ProviderUsageEvidence.from(policy,execution,new ProviderCostCalculator().fromEnvironment(policy,execution.result()),correlationId);
        try{
            JsonNode root=JSON.readTree(execution.result().output());
            if(!root.isObject()||root.size()!=3||!root.path("approved").isBoolean()||!root.path("summary").isTextual()||!root.path("criteria").isArray()||root.path("summary").asText().isBlank()||root.path("summary").asText().length()>2000)throw new IllegalArgumentException("Review output does not match schema");
            java.util.List<CriterionReview> assessments=new java.util.ArrayList<>();
            for(JsonNode item:root.path("criteria")){if(!item.isObject()||item.size()!=3||!item.path("statement").isTextual()||!item.path("status").isTextual()||!item.path("evidence").isTextual()||!java.util.List.of("PASS","FAIL").contains(item.path("status").asText())||item.path("evidence").asText().isBlank())throw new IllegalArgumentException("Review criterion does not match schema");assessments.add(new CriterionReview(item.path("statement").asText(),item.path("status").asText(),item.path("evidence").asText()));}
            java.util.Set<String> expected=new java.util.HashSet<>(task.acceptanceCriteria());java.util.Set<String> actual=assessments.stream().map(CriterionReview::statement).collect(java.util.stream.Collectors.toSet());if(assessments.size()!=task.acceptanceCriteria().size()||actual.size()!=assessments.size()||!actual.equals(expected))throw new IllegalArgumentException("Review must assess every criterion exactly once");boolean allPassed=assessments.stream().allMatch(item->"PASS".equals(item.status()));if(root.path("approved").asBoolean()!=allPassed)throw new IllegalArgumentException("Review approval conflicts with criterion outcomes");return new ReviewResult(allPassed,root.path("summary").asText(),java.util.List.copyOf(assessments),usage);
        }catch(Exception invalid){throw new GuardedPatchFailure("INVALID_REVIEW_OUTPUT",usage,invalid);}
    }
}
