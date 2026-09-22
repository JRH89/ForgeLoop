# Operator console and MCP gateway

The ForgeLoop console at `http://localhost:5173` is the operational surface for persisted delivery data. It shows the intake queue and each run's task dependencies, provider attempts, repair packages, acceptance criteria, verification gates, cost budget, evidence logs, audit history, and GitHub publication. The selected run refreshes every five seconds. Verification output is the bounded, credential-redacted evidence accepted by the control plane; the browser never connects to runner files or shells.

## Roles and control actions

`VIEWER` can inspect tenant-authorized runs. `OPERATOR` can cancel active runs and grant one additional repair attempt to a failed or held task. `ADMIN` can also approve a run after it reaches `READY_FOR_REVIEW`. Each mutation requires a literal confirmation value and records a digest-only audit event. Approval is persisted independently of automated verification, and the GitHub delivery service rejects unapproved runs.

The console asks for confirmation before cancel or approval and requires a bounded retry reason. Direct arbitrary task transitions are not part of the operator GraphQL API. Runner lease endpoints remain credential- and nonce-protected execution APIs.

## MCP configuration

The standalone stdio MCP server is an authenticated gateway to the same GraphQL operations:

```powershell
$env:FORGELOOP_GRAPHQL_URL = 'https://forgeloop.example.com/graphql'
$env:FORGELOOP_ACCESS_TOKEN = '<short-lived OIDC access token>'
$env:FORGELOOP_MCP_TOOL_GRANTS = 'get_run_context,read_verification_evidence,read_audit_timeline'
npm.cmd --prefix .\mcp-server start
```

Read-only tools are granted by default. Mutating grants are `approve_feature_run`, `cancel_feature_run`, and `retry_feature_task`; the token's persisted organization role is still authoritative. Unknown grants fail startup. The gateway uses static GraphQL documents, forwards only declared arguments, requires HTTPS except for loopback development, and never exposes Git, filesystem, Docker, browser, test, or shell execution. Those capabilities remain runner-local.

## Verification

Run `npm.cmd run check` in `frontend`, `npm.cmd test` and `npm.cmd run typecheck` in `mcp-server`, and build the control-plane Docker image to execute Maven verification. With the Compose stack running, `npm.cmd run test:e2e` in `frontend` verifies the live operator surface.
