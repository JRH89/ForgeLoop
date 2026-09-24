# Runner repository checkout

The runner does not require an operator to pre-clone every connected repository. After claiming a task, it requests a checkout grant bound to that active lease. The control plane derives the repository and base branch from the task and enabled repository connection, then issues a short-lived GitHub App installation token and records a digest-only audit event.

The runner validates that the grant names the expected repository. If the checkout is absent beneath the configured repositories root, it clones exactly that `owner/repository` and base branch. Existing checkouts receive an authenticated, pruned fetch of the configured base branch before worktree creation. Checkout creation is serialized inside a runner process so parallel tasks cannot race to create the same clone.

Credentials are supplied through Git's process environment configuration, not the remote URL, command arguments, filesystem credential helpers, or logs. Clone/fetch output is bounded and common GitHub token forms are redacted on failure. Task work still happens in isolated detached worktrees; integration remains the only role allowed to request a push grant.
