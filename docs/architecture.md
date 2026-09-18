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
| `backend` | Spring Boot GraphQL support-desk API and domain authorization. |
| `frontend` | React operator dashboard; it never makes authorization decisions. |
| `harness` | State machine, evidence writer, model-selection records, and worker contracts. |
| `mcp-server` | Stdio MCP tools for least-privilege delivery context and verification. |

## Design decisions

The domain owns ticket assignment through `TicketAssignmentService`; the GraphQL controller is an adapter. Repository access remains behind the service, and audit recording happens in the same transactional use case. This keeps policy testable and prevents UI or transport code from bypassing the authorization boundary.

Workers receive a `TaskAssignment` with a task id, acceptance criteria, checkout path, and a model selection. Production workers must use a distinct Git worktree and container per task. The included deterministic worker exists only to demonstrate orchestration without credentials; it is labelled as simulated in evidence and cannot be mistaken for a provider run.

Verification uses explicit named gates. Failed gates have an owning task category and a fixed repair budget; budget exhaustion moves a run to `BLOCKED`, never to success.

