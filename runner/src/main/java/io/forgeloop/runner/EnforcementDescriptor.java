package io.forgeloop.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable snapshot of enforcement authority sent with a task, independent of model-visible text. */
public final class EnforcementDescriptor {
    public static final String RULES_VERSION = "3";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String writeBoundary;
    private final List<String> testPathGlobs;
    private final RunnerRedPrerequisite redPrerequisite;
    private final List<String> protectedPathGlobs;
    private final Boolean allowWorkflowChanges;
    private final String finishGate;
    private final List<String> gateNames;
    private final List<String> checkNames;
    private final List<String> ruleNames;
    private final List<String> afterHookNames;
    private final String sha256;

    private EnforcementDescriptor(String writeBoundary, List<String> testPathGlobs, RunnerRedPrerequisite redPrerequisite,
                                  List<String> protectedPathGlobs, Boolean allowWorkflowChanges, String finishGate,
                                  List<String> gateNames) {
        this.writeBoundary = writeBoundary;
        this.testPathGlobs = immutableAllowingNulls(testPathGlobs);
        this.redPrerequisite = redPrerequisite;
        this.protectedPathGlobs = immutableAllowingNulls(protectedPathGlobs);
        this.allowWorkflowChanges = allowWorkflowChanges;
        this.finishGate = finishGate;
        this.gateNames = immutableAllowingNulls(gateNames);
        this.checkNames = redPrerequisite == null ? List.of("enforcement-config") : List.of("enforcement-config", "red-prerequisite");
        List<String> rules = new ArrayList<>(List.of("credential-files", "protected-paths"));
        if (!"ANY".equals(writeBoundary)) rules.add("write-boundary");
        rules.add("secret-content");
        if (finishGate != null && !finishGate.isBlank()) rules.add("finish-gate");
        this.ruleNames = List.copyOf(rules);
        this.afterHookNames = List.of("credential-files", "result-redaction");
        this.sha256 = Hashing.sha256(canonicalJson());
    }

    /** Builds a descriptor only from server-dispatched task policy and gate names. */
    public static EnforcementDescriptor of(RunnerTask task, List<LoopGate> gates) {
        if (task == null) throw new IllegalArgumentException("Dispatched runner task is required");
        List<String> names = gates == null ? List.of() : gates.stream().map(LoopGate::name).toList();
        RunnerLoopEnforcement enforcement = task.loopEnforcement();
        return fromDispatched(task.writeBoundary(), task.testPathGlobs(), task.redPrerequisite(),
                enforcement == null ? List.of() : enforcement.protectedPaths(),
                enforcement == null ? Boolean.FALSE : enforcement.allowWorkflowChanges(),
                enforcement == null ? null : enforcement.finishGate(), names);
    }

    /** Kept explicit so malformed wire inputs can be represented and held by preflight instead of defaulted. */
    public static EnforcementDescriptor fromDispatched(String writeBoundary, List<String> testPathGlobs,
                                                        RunnerRedPrerequisite redPrerequisite,
                                                        List<String> protectedPathGlobs, Boolean allowWorkflowChanges,
                                                        String finishGate, List<String> gateNames) {
        return new EnforcementDescriptor(writeBoundary, testPathGlobs, redPrerequisite, protectedPathGlobs,
                allowWorkflowChanges, finishGate, gateNames);
    }

    public static EnforcementDescriptor defaults(List<LoopGate> gates) {
        List<String> names = gates == null ? List.of() : gates.stream().map(LoopGate::name).toList();
        return fromDispatched("ANY", List.of(), null, List.of(), false, null, names);
    }

    public String writeBoundary() { return writeBoundary; }
    public List<String> testPathGlobs() { return testPathGlobs; }
    public RunnerRedPrerequisite redPrerequisite() { return redPrerequisite; }
    public List<String> protectedPathGlobs() { return protectedPathGlobs; }
    public Boolean allowWorkflowChanges() { return allowWorkflowChanges; }
    public String finishGate() { return finishGate; }
    public List<String> gateNames() { return gateNames; }
    public List<String> checkNames() { return checkNames; }
    public List<String> ruleNames() { return ruleNames; }
    public List<String> afterHookNames() { return afterHookNames; }
    public String sha256() { return sha256; }

    /** Stable audit snapshot persisted in LOOP_STARTED, including raw inputs and the rules digest. */
    public Map<String, Object> journalValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rulesVersion", RULES_VERSION);
        value.put("writeBoundary", writeBoundary);
        value.put("testPathGlobs", testPathGlobs);
        value.put("redPrerequisite", redPrerequisiteValue());
        value.put("protectedPathGlobs", protectedPathGlobs);
        value.put("allowWorkflowChanges", allowWorkflowChanges);
        value.put("finishGate", finishGate);
        value.put("gateNames", gateNames);
        value.put("checks", checkNames);
        value.put("rules", ruleNames);
        value.put("afterHooks", afterHookNames);
        value.put("sha256", sha256);
        return Collections.unmodifiableMap(value);
    }

    private String canonicalJson() {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("rulesVersion", RULES_VERSION);
        canonical.put("writeBoundary", writeBoundary);
        canonical.put("testPathGlobs", testPathGlobs);
        canonical.put("redPrerequisite", redPrerequisiteValue());
        canonical.put("protectedPathGlobs", protectedPathGlobs);
        canonical.put("allowWorkflowChanges", allowWorkflowChanges);
        canonical.put("finishGate", finishGate);
        canonical.put("gateNames", gateNames);
        canonical.put("checks", checkNames);
        canonical.put("rules", ruleNames);
        canonical.put("afterHooks", afterHookNames);
        try { return JSON.writeValueAsString(canonical); }
        catch (JsonProcessingException impossible) { throw new IllegalStateException("Enforcement descriptor could not be fingerprinted", impossible); }
    }

    private static List<String> immutableAllowingNulls(List<String> values) {
        if (values == null) return null;
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private Object redPrerequisiteValue() {
        if (redPrerequisite == null) return null;
        Map<String, Object> value = new TreeMap<>();
        value.put("testTaskId", redPrerequisite.testTaskId());
        value.put("targetSha", redPrerequisite.targetSha());
        value.put("evidenceDigest", redPrerequisite.evidenceDigest());
        return value;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
