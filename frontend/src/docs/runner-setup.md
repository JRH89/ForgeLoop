## Install a runner and configure provider keys

The runner runs on your own machine or dedicated server, not in your browser.
GitHub sign-in, repository installation, runner enrollment, and model-provider
credentials are separate. Connecting a repository does not install a runner.

### Guided installation (recommended)

Open **Harness & policy → Install and connect a runner**. Administrators can
generate a single-use enrollment token there; other roles ask their administrator.
The panel lists runner heartbeats so you can confirm the connection.

1. Download the runner ZIP and its SHA-256 checksum from that panel. Verify the
   checksum, review the included scripts, and extract into a private permanent folder.
2. Install prerequisites: Java 21+, Git, and Docker with Linux containers. The
   package includes the tested runner JAR; no source checkout or Maven is needed.
3. On Windows run `powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Runner.ps1`.
   Setup prompts for the control-plane URL, enrollment token, provider/model,
   API key, and input/output USD prices per million tokens.
4. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Runner.ps1` when
   ready to process eligible issues. Setup itself makes no paid model request.

Add `-StartAtLogin` to installation for an optional hidden, current-user scheduled
task. Docker must also start at login. It is not an always-on server service and
does not run before login. Without this option, keep the foreground terminal open.
Windows keys are encrypted with DPAPI for the installing user and machine;
they cannot be moved to another host. Local administrators can still inspect
running processes. The package README includes direct Java commands for Linux/macOS.

Stop the worker before rotating keys or changing models/prices, rerun setup
without `-StartAtLogin`, and restart. Existing identity is retained. Upgrades must
preserve identity, configuration, policy and encrypted secrets; replace binaries
and scripts only. Never enroll multiple workers with the same identity.

Enrollment tokens expire after 15 minutes. The installer supplies them on stdin,
not as visible process arguments. The browser never asks for your provider key.
Advanced administrators can also issue tokens through the authenticated GraphQL API:

```graphql
mutation EnrollRunner($organizationId: String!) {
  issueRunnerRegistrationToken(organizationId: $organizationId)
}
```

Use your actual organization ID and administrator authentication. Never disable
authentication to enroll a runner. Treat the response as a secret; do not paste
it in issues or logs. Enrollment tokens are not GitHub installation IDs or API keys.

### Advanced: Windows source installation

Prerequisites: Git, Java 21, Maven, and a running Docker daemon using Linux
containers. Use a dedicated account with access only to the repositories and
Docker daemon it needs. Docker access is privileged; do not run untrusted work
on a shared production host.

Clone ForgeLoop and run these commands from its root. Keep runner data outside
the checkout. The example uses PowerShell and starts the Java runner directly,
so Docker can resolve the host worktree paths without container path mapping.

```powershell
git clone https://github.com/JRH89/ForgeLoop.git
cd ForgeLoop
mvn -f runner/pom.xml verify
if ($LASTEXITCODE -ne 0) { throw 'Runner build failed' }
$runnerJar = (Resolve-Path runner/target/runner-0.1.0.jar).Path
$runnerRoot = Join-Path $env:LOCALAPPDATA 'ForgeLoopRunner'
New-Item -ItemType Directory -Force -Path $runnerRoot | Out-Null
$env:FORGELOOP_RUNNER_STATE_FILE = Join-Path $runnerRoot 'identity'
$controlPlane = 'https://forgeloop.hookerhillstudios.com'
Copy-Item runner/provider-policy.example.json (Join-Path $runnerRoot 'provider-policy.json')
```

Do not overwrite an existing provider policy or identity when upgrading. Restrict
the runner directory to your account using your operating system's permissions.
It contains the runner credential, repository clones, worktrees, and lease state.

### Choose models and set API keys

Edit `provider-policy.json` before starting work. Each role has a `provider`,
`model`, and `maxAttempts` (1–3). The sample mixes Anthropic and OpenAI: if you
only have an Anthropic key, change every provider-backed role to `anthropic`
and a model ID enabled for your account. Organization provider restrictions
still apply. Example model IDs are configuration examples, not a guarantee of
availability. A subscription to a chat website is not an API-credit balance.

| Provider | Runner process environment variable |
| --- | --- |
| Anthropic | `ANTHROPIC_API_KEY` |
| OpenAI | `OPENAI_API_KEY` |
| Gemini | `GEMINI_API_KEY` |
| Local compatible endpoint | `FORGELOOP_LOCAL_PROVIDER_URL`, optional `FORGELOOP_LOCAL_PROVIDER_API_KEY` |

Keys belong only on the runner. Do not put them in the dashboard, repository,
provider-policy JSON, or control-plane `.env`. The Java CLI does not automatically
load an `.env` file. Supply environment variables to the process that starts it.
For example, securely prompt for an Anthropic key without storing its literal
value in PowerShell history:

```powershell
$secret = Read-Host 'Anthropic API key' -AsSecureString
$env:ANTHROPIC_API_KEY = [System.Net.NetworkCredential]::new('', $secret).Password
Remove-Variable secret
$model = Read-Host 'Exact Anthropic model ID from your provider policy'
java -cp $runnerJar io.forgeloop.runner.RunnerMain provider-health anthropic $model
```

This health check makes a small, billable provider request. Success reports
request and token metadata. Insufficient credits are a provider billing problem,
not a GitHub connection problem. Skip the health request while credits are unavailable.

These variables last only in this terminal and its child processes. A new terminal
or reboot requires re-injection. For an unattended service, use the host's secret
manager or restricted service configuration; do not use a repository-tracked file.
Restart/recreate a runner after changing its environment. For Docker, explicitly
pass selected variables, for example `-e ANTHROPIC_API_KEY`; never pass the entire
control-plane `.env`. Anyone administering the runner host can access its secrets.

### Enroll, verify connectivity, and start

Run enrollment once with the token supplied by your administrator:

```powershell
$secret = Read-Host 'One-time enrollment token' -AsSecureString
$enrollmentToken = [System.Net.NetworkCredential]::new('', $secret).Password
java -cp $runnerJar io.forgeloop.runner.RunnerMain register $controlPlane $enrollmentToken 'my-runner' 'git,provider,docker'
Remove-Variable enrollmentToken,secret
java -cp $runnerJar io.forgeloop.runner.RunnerMain heartbeat $controlPlane $env:FORGELOOP_RUNNER_STATE_FILE
```

The current CLI accepts enrollment tokens as process arguments. The prompt avoids
literal shell-history storage, but local process administrators may inspect the
argument while enrollment runs. Use a trusted host. Do not rerun registration
over an existing identity; obtain a fresh token if an unused token expires.

After `Runner heartbeat accepted`, start the worker in the same terminal:

```powershell
java -cp $runnerJar io.forgeloop.runner.RunnerMain serve $controlPlane $env:FORGELOOP_RUNNER_STATE_FILE (Join-Path $runnerRoot 'repositories') (Join-Path $runnerRoot 'worktrees') (Join-Path $runnerRoot 'provider-policy.json') 'src' (Join-Path $runnerRoot 'leases') 1
```

Keep that terminal open. Closing it stops this foreground runner. `serve` polls
continuously; `work-until-idle` uses the same arguments but exits after bounded
idle polling. The final number is local parallelism (1–16); start at 1. The `src`
argument is a legacy fallback path scope, not permission to bypass planned owned
paths. Repository policy selects verification commands and images separately.

The runner automatically clones authorized repositories through short-lived,
lease-bound GitHub App grants. No personal GitHub token or manual pre-clone is
required. It contacts the control plane outbound; it does not need a public
inbound port or its own Cloudflare tunnel. It also needs outbound access to
GitHub, selected providers, registries, and policy-allowed dependencies.

### Restart, upgrades, and troubleshooting

On restart, restore the local variables and provider environment, reuse the same
identity and directories, and run `serve` again. Do not enroll on every startup.
For unattended operation, configure a supervised service or container restart
policy and durable state mounts. The source installation above does not install
such a service automatically. Stop the worker gracefully before replacing its
binary and retain its state when upgrading.

- **No work:** verify the heartbeat, organization/repository ownership, capability
  requirements, and that the run is eligible for dispatch.
- **Missing key:** ensure the variable is present in the runner process, not only
  the control-plane process or another terminal. Never print its value to debug.
- **Model/credit error:** check all role entries, account model access, and API credits.
- **Docker failure:** verify the daemon is running and the runner account can use it.
  Containerized runners additionally require daemon-visible workspace mapping.
- **Lost identity:** ask an administrator to manage re-enrollment; do not copy
  another runner's identity or invent credentials.

Runner commands and container workspace/socket configuration are also documented
in the repository's `runner/README.md`.

### Prices, costs, and budgets

The installer writes `inputUsdPerMillion` and `outputUsdPerMillion` alongside
provider/model/maxAttempts in the runner-local policy. For source installations,
add both nonnegative numeric fields to each role (or a `default` entry). Example
shape using illustrative rates, not current vendor prices:

```json
{"default":{"provider":"anthropic","model":"YOUR_MODEL_ID","maxAttempts":2,"inputUsdPerMillion":3,"outputUsdPerMillion":15}}
```

Use rates from your actual provider account. Policy rates override legacy pricing
environment variables. Costs are estimates for recorded input/output tokens,
not provider invoices; caching, tools, tiers and other charges can differ.
Unknown older requests remain explicitly unpriced, not $0.00, and are not
retroactively altered. Budget remaining is based on priced usage only, so missing
rates reduce budget accuracy. Future work needs a configured price to report cost.

### Assignment-gated intake

In **Repositories**, set **Required GitHub assignee** to a login without `@`.
Both the intake label and that assignee must be present before a new issue starts.
Leave it blank to use label-only intake. Choose a login GitHub lets you assign in
that repository; the setting does not make an arbitrary App bot assignable.
Assigning a labeled issue or labeling an assigned issue both trigger evaluation.
It applies to future intake, not cancellation of existing work. Manual submissions
remain explicit operator requests and do not use this GitHub-only gate.

### Clear the intake queue and follow progress

Use **Archive** on terminal runs, or cancel active work first. **Show archived runs**
lets you inspect them and **Restore** puts them back. Archiving keeps evidence,
audit records, costs, and GitHub issue deduplication; it never deletes GitHub work.
Permanent destruction is deliberately not the queue-cleanup action.

The queue and selected run refresh every two seconds while visible, with slower
background polling and error backoff. The status line shows the last successful
refresh and connection failures. Runner events include provider start/completion,
token counts and ten-second elapsed-time progress during provider/verification
work. These are safe metadata events, not raw model reasoning or streamed secrets.
Verification output remains in the completed evidence. Older runners must be
upgraded to emit the additional event types.
