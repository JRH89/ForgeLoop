# ForgeLoop runner package

Prerequisites: Java 21+, Git and Docker with Linux containers. Use a dedicated
account/host: Docker access grants extensive host privileges. This package
contains the tested runner JAR; Maven and the source checkout are not required.

Download the adjacent SHA-256 file and compare it to `Get-FileHash
./forgeloop-runner.zip -Algorithm SHA256` before extracting. The checksum detects
corruption; it is not an independent signature. Only download from your trusted
ForgeLoop deployment over HTTPS. Review scripts before executing.

## Windows

Extract to a private permanent directory. From that directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Runner.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Runner.ps1
```

Generate an enrollment token from the dashboard's **Harness & policy → Install
and connect a runner**. Setup prompts for the URL, token, provider, model, API key,
and input/output prices in USD per million tokens. It registers and checks the
heartbeat, but does not call the provider. Starting the worker can incur charges
as soon as eligible issues exist. Tokens expire after 15 minutes and work once.

Add `-StartAtLogin` to installation for an optional current-user scheduled task.
The task starts hidden at login (not before login), restarts after failure, and
does not start immediately. Configure Docker Desktop to start at login too.
The ordinary foreground runner stops when you close its terminal. Do not run two
instances with the same identity. Stop it before upgrading or changing settings.

The API key is stored using Windows DPAPI for this user and machine, never in
plain text JSON. Local administrators can still inspect running process memory.
Re-run setup to rotate the key or change the provider/model/prices; it reuses the
identity. Omit `-StartAtLogin` when updating an existing scheduled installation.
Edit per-role overrides in provider-policy.json for multiple models; additional
provider keys must be injected into the service process environment.

To uninstall: stop the worker, remove its specifically named scheduled task in
Task Scheduler, then securely remove this installation directory after deciding
whether to retain identity/evidence. Do not delete your project repositories.

## Linux/macOS

The same JAR runs directly with Java 21+, Git and Docker. Use a private runner
directory (`chmod 700`), `umask 077`, and a secret manager for provider environment
variables. Copy/edit provider-policy.example.json; set prices in each role or a
`default` entry. The default example has no prices and uses multiple providers.
Enrollment is available without putting the token in command arguments:

```sh
export FORGELOOP_RUNNER_STATE_FILE="$PWD/identity"
java -cp runner.jar io.forgeloop.runner.RunnerMain register-stdin https://YOUR_FORGELOOP_HOST my-runner git,provider,docker
# Paste the one-time token on stdin, then Enter. It is not a shell command.
java -cp runner.jar io.forgeloop.runner.RunnerMain heartbeat https://YOUR_FORGELOOP_HOST "$PWD/identity"
java -cp runner.jar io.forgeloop.runner.RunnerMain serve https://YOUR_FORGELOOP_HOST "$PWD/identity" "$PWD/repositories" "$PWD/worktrees" "$PWD/provider-policy.json" src "$PWD/leases" 1
```

Input is visible on an ordinary terminal: use a trusted terminal or your secret
manager's pipe. Inject ANTHROPIC_API_KEY, OPENAI_API_KEY or GEMINI_API_KEY before
starting. Use your service manager for unattended startup. Windows DPAPI files
are not portable. macOS Docker file-sharing must include the worktree directory.

Prices are operator-maintained estimates for reported input/output tokens, not
invoices; caching, tools, tier discounts and provider-specific charges may differ.
Unknown historical costs are not silently rewritten. When changing rates, stop
the worker, update policy, and restart. Back up state securely before upgrades;
replace binaries/scripts only, retaining identity, config, policy and secrets.
