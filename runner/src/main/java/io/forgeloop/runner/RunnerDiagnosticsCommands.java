package io.forgeloop.runner;

import java.nio.file.Path;

/** Explicit provider diagnostics and offline record verification, independent of task dispatch. */
final class RunnerDiagnosticsCommands {
    private RunnerDiagnosticsCommands() { }
    /** Executes one bounded, non-repository provider request to prove runner-local credentials work without exposing them. */
    static void providerHealth(String[] arguments) throws Exception {
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: provider-health <anthropic|openai|gemini|local> <model>");
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(arguments[1], arguments[2], 1);
        ProviderClient provider = new ProviderClientFactory().create(policy);
        ProviderResult result = provider.execute(new ProviderRequest(arguments[2], "You are a credential health check.", "Reply with exactly: ForgeLoop provider ready.", 128));
        System.out.println("Provider health check passed. request=" + result.providerRequestId() + " inputTokens=" + result.inputTokens() + " outputTokens=" + result.outputTokens());
    }

    /** Explicitly captures one fixed 128-token live response without printing it or overwriting a fixture. */
    static void providerFixtureCapture(String[] arguments) throws Exception {
        if (arguments.length < 4 || arguments.length > 5 || !"--confirm-live-call".equals(arguments[3])
                || arguments.length == 5 && !"--desktop-key".equals(arguments[4])) {
            throw new IllegalArgumentException("Usage: provider-fixture-capture <anthropic|openai|gemini|local> <model> --confirm-live-call [--desktop-key]");
        }
        String provider = arguments[1], model = arguments[2];
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(provider, model, 1);
        final String credential;
        if (arguments.length == 5) {
            if ("local".equals(provider)) throw new IllegalArgumentException("Local provider credentials are configured through the local provider environment");
            credential = new DesktopSecretStore(DesktopFiles.directory(), provider).load();
        } else credential = null;
        ProviderClientFactory codecs = new ProviderClientFactory();
        ProviderFixtureCapture.Captured capture = new ProviderFixtureCapture().capture(provider, model,
                () -> codecs.create(policy, credential), Path.of("runner", "src", "test", "resources", "provider-fixtures"),
                java.time.Instant.now());
        System.out.println("Live provider fixture captured. provider=" + provider + " model=" + model
                + " fixture=runner/src/test/resources/provider-fixtures/" + provider
                + " inputTokens=" + capture.inputTokens()
                + " outputTokens=" + capture.outputTokens());
    }

    /** Requires a live-recorded, pin-matched fixture before creating a client or sending one bounded health request. */
    static int providerDriftCheck(String[] arguments) throws Exception {
        if (arguments.length != 3 && (arguments.length != 4 || !"--desktop-key".equals(arguments[3]))) {
            System.err.println("Usage: provider-drift-check <anthropic|openai|gemini|local> <model> [--desktop-key]");
            return 2;
        }
        String provider = arguments[1], model = arguments[2];
        String credential = null;
        if (arguments.length == 4) {
            if ("local".equals(provider)) {
                System.err.println("Local provider credentials are configured through the local provider environment.");
                return 2;
            }
            credential = new DesktopSecretStore(DesktopFiles.directory(), provider).load();
        }
        final String savedCredential = credential;
        final ProviderFixtureStore.Fixture fixture;
        try { fixture = new ProviderFixtureStore().load(provider); }
        catch (Exception invalid) { System.err.println("Could not run provider drift check: fixture is unavailable or invalid."); return 2; }
        ProviderClientFactory codecs = new ProviderClientFactory();
        ProviderDriftProbe.Result result = new ProviderDriftProbe().run(provider, model, fixture,
                () -> codecs.create(new ProviderExecutionPolicy(provider, model, 1), savedCredential));
        if (result.exitCode() == 0) System.out.println(result.message());
        else {
            System.err.println(result.message());
            result.differences().forEach(path -> System.err.println("- " + path));
        }
        return result.exitCode();
    }

    /** Live two-turn check; output is limited to normalized stop reasons and token counts. */
    static void providerToolCheck(String[] arguments) throws Exception {
        if (arguments.length != 3) throw new IllegalArgumentException("Usage: provider-tool-check <anthropic|openai|gemini|local> <model>");
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy(arguments[1], arguments[2], 1);
        ConversationClient client = (ConversationClient) new ProviderClientFactory().create(policy);
        ProviderToolCheckResult result = new ProviderToolCheck().run(client, policy.model(), policy.maxAttempts());
        System.out.println("Provider tool check passed. firstStop=" + result.firstStopReason() + " secondStop=" + result.secondStopReason()
                + " inputTokens=" + result.inputTokens() + " outputTokens=" + result.outputTokens()
                + " attempts=" + result.firstAttempts() + "+" + result.secondAttempts());
    }

    /** Runs the record verifier without reading provider keys, control-plane credentials, or network state. */
    static int verifyRunRecord(String[] arguments, boolean journalOnly) {
        String command = journalOnly ? "verify-journal" : "verify-run";
        if (arguments.length != 3 && arguments.length != 5
                || arguments.length == 5 && !"--json".equals(arguments[3])) {
            System.err.println("Usage: " + command + (journalOnly
                    ? " <journal.jsonl> <repository-path> [--json <report-file>]"
                    : " <archive.zip> <repository-path> [--json <report-file>]"));
            return 2;
        }
        try {
            RunRecordVerifier verifier = new RunRecordVerifier();
            VerifyReport report = journalOnly
                    ? verifier.verifyJournal(Path.of(arguments[1]), Path.of(arguments[2]))
                    : verifier.verifyRun(Path.of(arguments[1]), Path.of(arguments[2]));
            report.printTo(System.out);
            if (arguments.length == 5) report.writeJson(Path.of(arguments[4]));
            return report.exitCode();
        } catch (Exception failure) {
            System.err.println("Run-record verification could not run: " + failure.getMessage());
            return 2;
        }
    }

    /** Generates and commits a schema-validated Claude patch only within operator-supplied policy prefixes. */
}
