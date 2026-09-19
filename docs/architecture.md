# Architecture

ForgeLoop separates orchestration from product delivery so neither controls the other.

```
Specification -> Planner -> Task graph -> isolated workers
                                      |        |
                                      v        v
                                  integration -> verification gates -> review
                                                         | failure
                                                         v
                                             bounded repair routing
```

## Modules

| Module | Responsibility |
| --- | --- |
| `control-plane` | Spring Boot GraphQL API for repository connections, delivery runs, task scheduling, evidence, and GitHub intake. |
| `frontend` | React ForgeLoop operator console; it never makes authorization decisions and does not contain target-application CRUD. |
| `runner` | Separately versioned local/self-hosted execution client; it clones connected repositories and runs isolated worktrees, containers, tests, browsers, and local MCP tools. |
| `harness` | State machine, evidence writer, model-selection records, and worker contracts. |
| `mcp-server` | Stdio MCP gateway for least-privilege control-plane run, policy, task, evidence, and cancellation operations. It cannot execute repository commands. |

## Design decisions

Repository connections own a versioned `RepositoryPolicy`: allowed issue labels, branch and pull-request rules, path limits, harness profile, commands, verification gates, tool permissions, budgets, and retention requirements. The control plane resolves every run from that policy and records its revision. This keeps target-specific framework choices out of the scheduler and makes the policy testable.

Workers receive a `TaskAssignment` with a task id, acceptance criteria, checkout path, selected repository policy, and model selection. Production workers must use a distinct Git worktree and container per task. The included deterministic worker exists only to demonstrate orchestration without credentials; it is labelled as simulated in evidence and cannot be mistaken for a provider run. Ticketly is one external repository used for validation and has no special runner logic.

Verification uses explicit named gates. Failed gates have an owning task category and a fixed repair budget; budget exhaustion moves a run to `BLOCKED`, never to success.
