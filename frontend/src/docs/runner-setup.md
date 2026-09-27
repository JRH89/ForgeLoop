## Desktop downloads and updates

Open [Download runner](/app/runner-downloads) for the latest published desktop
preview. Downloads come from GitHub Releases and do not require signing in.
Choose Windows x64, macOS Apple Silicon (arm64) or Intel (x64), or the Linux x64
DEB package for Debian/Ubuntu desktops. Java is bundled; Git and Docker are required.

Preview installers are unsigned and macOS previews are not notarized. Windows
and macOS may warn or block installation; use previews only if your device policy
permits them. Signing remains a separate release milestone.

Expand **Verify download** to copy SHA-256 and see the command for your platform.
Compare the entire hash. A mismatch means the file must not be installed.
Checksums confirm file integrity, not the publisher's identity.

For updates, pause the runner, wait for current work to finish, close it, install
the newer package and reopen it. Your identity, provider keys and settings are
retained outside the installation folder. The website discovers new releases
automatically within about five minutes; the app does not install updates itself.

## Navigation and usage tracking

The browser runner-connection page displays your runner name and verification
fingerprint in a dedicated panel. Compare it with the desktop app, then select
the confirmation checkbox to enable approval. Approval alone never starts paid work.

On mobile, open the hamburger button in the header for the navigation dropdown.
It includes **Support** and **Sign out**, closes when you choose a page, and
supports Escape to close. Desktop keeps the sidebar and a Sign out button in the
header. Expand **User guide**, the fifth sidebar item, to choose Quick start,
Runner setup & API keys, Repositories, Runs, Settings, Delivery, or Troubleshooting.
Choosing a section opens the guide at that section and closes the mobile menu;
expanding the submenu itself keeps the mobile menu open.

On phones, Runs uses labeled cards rather than a wide table. Each card keeps
repository, status, progress, cost, start time, and archive controls visible.
Tap a card to open its task/evidence details. Archive is a separate action and
still requires confirmation. Long specifications and logs stay within their
panels; wide code or Markdown tables may scroll inside their own evidence area.

**Usage & costs** replaces Analytics. Filter by repository and the last 7, 30,
or 90 UTC calendar days to see daily estimated spending, cost by model, token
usage, pricing coverage, harness comparison, and individual run costs. Costs are
grouped by the provider telemetry's recording time, not the run creation date.
Archived runs remain included. The all-time delivery overview is explicitly
separate and does not change with these filters.

Dollar amounts are estimates from the runner's public base-rate lookup or manual
override, not a provider invoice or account balance. **N/A** means no priced usage is available;
partially priced totals exclude unknown prices and show an unpriced-record count.
The desktop Provider tab looks up public base rates for supported models; use
manual prices only for account-specific terms or a missing catalog entry. Historical
unknown costs are not silently backfilled. Use **Budget & execution settings**
for organization limits and approval policies; only administrators can change
those policies. Usage viewing is read-only and makes no model calls. Updates
refresh every ten seconds; failures show a stale-data warning and a retry button.

## Start with the setup checklist

Open **Getting started** in the dashboard sidebar, or **Open setup checklist**
on Runs. User guide remains the fifth sidebar item. The checklist refreshes
organization-scoped repository, runner, policy, and run metadata every 10 seconds:

1. **Connect GitHub.** An administrator installs the ForgeLoop GitHub App and
   selects repositories. GitHub login and repository installation are separate.
2. **Select your repository.** Confirm its base branch and harness. ForgeLoop
   works with any supported connected repository; no special demo repository is required.
3. **Connect and check your runner.** Download the desktop preview, approve the
   matching browser fingerprint, and save provider settings locally. Use **Check
   saved key locally** and **Check saved connection**. Neither calls a model.
4. **Review intake and safety settings.** Check the exact issue label, optional
   required assignee, budgets, harness, verification gates, human approval, and
   auto-merge policy. Administrators change intake in Repositories and execution
   settings in Harness & policy.
5. **Create and follow your first issue.** Draft a small change with observable
   acceptance criteria. The draft link does not apply intake labels or assignees.
   Adding the configured label/assignee can start paid work if a runner is running.

**Check issue intake** reads one existing issue from GitHub only when clicked.
Enter its issue number to see whether its label, assignee, open state, and body
meet intake rules. It does not edit the issue, replay webhooks, or submit work.
Passing this check does not prove webhook delivery or that a run has started.
Intake handles opened, labeled, assigned, and reopened events; a description edit
alone is not an intake trigger. If rules pass but no run appears, inspect the
GitHub App's webhook deliveries before retrying anything.

**What is waiting?** explains persisted approval, failure, budget, and heartbeat
signals for unarchived runs in the selected repository. Open Runs for detailed
events and evidence. A heartbeat within 60 seconds means recent contact, not
proof of provider funding, valid credentials, matching capabilities, or an idle
worker. Active work may delay heartbeats. The website cannot inspect your locally
stored key, Git, or Docker. Stale/read failures are shown explicitly; use Refresh
readiness to retry. No setup check calls a model. Starting a runner with queued
work can incur provider charges, so keep it paused when avoiding API spending.

## Install a runner and configure provider keys

The runner runs on your own machine or dedicated server, not in your browser.
GitHub sign-in, repository installation, runner enrollment, and model-provider
credentials are separate. Connecting a repository does not install a runner.

### Desktop setup (development preview)

Preview installer 1.0.3 includes the ForgeLoop favicon for the native launcher
and operating-system shortcuts. To replace an older Java-branded shortcut,
pause work, close the app, and install the newer package. Saved runner state
stays outside the installation directory. CI packages are unsigned previews;
the download page discovers explicitly published GitHub Releases automatically
and displays their version, platform, release notes and optional SHA-256 checks.

The new desktop app includes Java and guides you through **Connect → Provider →
Run**. Connect opens GitHub sign-in and an administrator approval page; compare
the fingerprint in both windows. No enrollment token is copied. Choose a model,
save your API key in your operating system's secure storage, check Git/Docker,
and explicitly start work. The Provider step looks up public base token rates
for the selected provider and model without using your API key. It shows the
lookup date and published source; use manual prices for account-specific terms.
If no verified rate is available or the catalog cannot be reached, save the
model without prices and cost will display N/A rather than $0. No provider call
is made during setup.

On reopening, **Run** is selected when your setup is saved. An empty key field
does not mean your key was lost: leave it blank to retain the stored key.
**Check saved key locally** verifies protected storage without calling the model;
**Check saved connection** sends only a heartbeat. Neither spends model credits.
**Reopen approval page** and **Cancel connection** recover an interrupted pairing
attempt without editing URLs. Old installations without a saved display name
show **Previously connected runner**; do not re-enroll just to change that label.
**Export safe diagnostics** saves runtime/status information, not credentials or
raw task logs. Log history is session-only. Keep **Start work at sign-in** off
when you do not want automatic API spending.

Use **Pause after current work** before closing or updating. Optional sign-in
startup can spend API credits and requires Docker and an unlocked keyring.
Windows uses DPAPI, macOS uses Keychain, and Linux requires libsecret tools and
an unlocked desktop keyring. Headless Linux servers should use the CLI below.

#### Connection recovery (desktop preview 1.0.2)

The Run footer distinguishes **Connecting**, **Connected / waiting**, **Working**,
**Offline / reconnecting**, and **Stopped / attention required**. Hover over it
for the last successful control-plane poll time. A running process alone is not
reported as a healthy connection. **Pausing** means the current work or request
must finish before exit.

When idle polling loses the server or receives a temporary service error, the
runner retries after 2, 4, 8, then at most 16 seconds. It claims no new tasks until
both heartbeat and task discovery succeed. Recovery reuses the saved identity;
do not reconnect/re-enroll simply because the server restarted. HTTP requests
have a 30-second timeout; Pause interrupts retry waiting but not an in-flight
request or active work. Recovery can resume eligible paid work if you previously
started the runner, so use Pause if you do not want work to resume.

A rejected credential, authorization failure, or incompatible request stops the
worker instead of retrying forever. Ask your administrator to check runner access
and client compatibility, use **Check saved connection**, and explicitly select
**Start runner** after correction. Do not delete saved identity or key files to
troubleshoot a temporary outage. Server response bodies are excluded from these
diagnostics.

Connection status reflects polling between batches, not continuous connectivity
during a long task. This does not checkpoint or replay interrupted model calls;
existing server lease-expiry/retry rules still govern interrupted tasks. Reopening
the desktop restores setup but does not start work unless you explicitly opted
into sign-in startup. Real OS reboot and funded in-flight delivery verification
remain separate release checks.

The **Desktop installers and updates** link in Harness & policy reports release
availability. Public preview installers are unsigned; signed production installers
still require signing/notarization and release verification.
The existing guided CLI installation below remains an advanced fallback.

### Guided CLI installation (advanced fallback)

Open **Harness & policy → Install and connect a runner**. Administrators can
generate a single-use enrollment token there; other roles ask their administrator.
The panel lists runner heartbeats so you can confirm the connection.

1. Download the runner ZIP and its SHA-256 checksum from that panel. Verify the
   checksum, review the included scripts, and extract into a private permanent folder.
2. Install prerequisites: Java 21+, Git, and Docker with Linux containers. The
   package includes the tested runner JAR; no source checkout or Maven is needed.
3. On Windows run `powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Runner.ps1`.
   Setup prompts for the control-plane URL, enrollment token, provider/model,
   API key. It looks up public base rates; manual input/output USD prices per
   million tokens are optional. Unknown prices remain N/A.
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

The guided installer looks up public base rates automatically and writes
`inputUsdPerMillion` and `outputUsdPerMillion` alongside
provider/model/maxAttempts only when rates are known or manually overridden.
For source installations, add both nonnegative numeric fields to each role (or
a `default` entry) if you want estimates. Example shape using illustrative
rates, not current vendor prices:

```json
{"default":{"provider":"anthropic","model":"YOUR_MODEL_ID","maxAttempts":2,"inputUsdPerMillion":3,"outputUsdPerMillion":15}}
```

Override with rates from your actual provider account when they differ. Policy rates override legacy pricing
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
