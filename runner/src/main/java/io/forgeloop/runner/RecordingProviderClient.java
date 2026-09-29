package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records each provider HTTP try locally while preserving the wrapped adapter's execution semantics. */
final class RecordingProviderClient implements ProviderClient {
    private final ProviderClient delegate;
    private final ProviderExecutionPolicy policy;
    private final StepJournal journal;
    private int tryNumber;

    RecordingProviderClient(ProviderClient delegate, ProviderExecutionPolicy policy, StepJournal journal) {
        if (delegate == null || policy == null || journal == null) throw new IllegalArgumentException("Recording provider inputs are incomplete");
        this.delegate = delegate; this.policy = policy; this.journal = journal;
    }

    @Override public synchronized ProviderResult execute(ProviderRequest request) throws ProviderException {
        int currentTry = ++tryNumber;
        String body;
        try { body = new ProviderClientFactory().requestBody(policy.provider(), request); }
        catch (Exception failure) { throw new ProviderException("Provider request could not be serialized for the run record", false, failure); }
        try {
            Map<String, Object> requested = new LinkedHashMap<>();
            requested.put("call", 1); requested.put("try", currentTry); requested.put("model", request.model());
            requested.put("instructions", request.instructions()); requested.put("input", request.input());
            requested.put("maxOutputTokens", request.maxOutputTokens()); requested.put("outputSchema", request.outputSchema());
            requested.put("requestSha256", sha256(body.getBytes(StandardCharsets.UTF_8)));
            journal.append("CALL_REQUESTED", requested);
        } catch (IOException failure) {
            throw new ProviderException("Run journal could not record the provider request", false, failure);
        }
        try {
            ProviderResult result = delegate.execute(request);
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("call", 1); completed.put("try", currentTry); completed.put("responseBody", result.responseBody());
            completed.put("answeredModel", result.answeredModel()); completed.put("inputTokens", result.inputTokens());
            completed.put("outputTokens", result.outputTokens()); completed.put("providerRequestId", result.providerRequestId());
            journal.append("CALL_COMPLETED", completed);
            return result;
        } catch (ProviderException failure) {
            appendFailure(currentTry, failure);
            throw failure;
        } catch (IOException failure) {
            throw new ProviderException("Run journal could not record the provider response", false, failure);
        }
    }

    private void appendFailure(int currentTry, ProviderException failure) throws ProviderException {
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("call", 1); fields.put("try", currentTry); fields.put("retryable", failure.retryable());
            String summary = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            String safeSummary = EvidenceRedactor.redactTokens(summary);
            fields.put("summary", safeSummary.substring(0, Math.min(safeSummary.length(), 400)));
            journal.append("CALL_FAILED", fields);
        } catch (IOException journalFailure) {
            failure.addSuppressed(journalFailure);
            throw new ProviderException("Run journal could not record the provider failure", false, failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }
}
