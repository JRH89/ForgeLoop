Absolutely. I’d combine them into one project with a very clear identity:

# ForgeLoop

**A distributed, multi-agent software delivery harness that turns feature specifications into verified pull requests using parallel autonomous agents, isolated execution environments, MCP-based tooling, and closed-loop verification.**

The key idea is that ForgeLoop is **not another coding assistant**. It is the system around coding agents that gives them context, delegates complete units of work, verifies their output, routes failures back for repair, and produces evidence that the finished feature actually satisfies the specification.

It would consist of a **web-based control plane plus self-hosted runners**.

---

## 1. What ForgeLoop does

A user connects a repository and gives ForgeLoop a feature specification:

> Add ticket assignment. Organization admins can assign tickets to members of their organization. Add the GraphQL API, authorization and validation, React interface, optimistic UI handling, audit event, tests, and browser verification. Users must never be able to assign tickets to members of another organization.

ForgeLoop takes that from specification to PR:

```text
Feature Specification
        │
        ▼
┌───────────────────┐
│ Context Builder   │
│                   │
│ Repository        │
│ Architecture      │
│ Rules             │
│ Relevant files    │
│ MCP tools         │
└─────────┬─────────┘
          ▼
┌───────────────────┐
│ Planner Agent     │
└─────────┬─────────┘
          │
          ▼
      Task Graph
          │
    ┌─────┼──────────────┐
    │     │              │
    ▼     ▼              ▼
Backend  Frontend       Test
Agent    Agent           Agent
    │     │              │
    └─────┼──────────────┘
          ▼
┌─────────────────────┐
│ Integration         │
└──────────┬──────────┘
           ▼
┌────────────────────────────┐
│ Verification               │
│                            │
│ Build                      │
│ Unit tests                 │
│ Integration tests          │
│ Type checking              │
│ Container health           │
│ GraphQL contract           │
│ Browser/Playwright tests   │
│ Security checks            │
│ Acceptance criteria        │
└─────────────┬──────────────┘
              │
         ┌────┴────┐
       Fail       Pass
         │          │
         ▼          ▼
     Repair       Review
      Agent        Agent
         │          │
         └──↺       ▼
                 Pull Request
```

The fundamental rule is:

> **An agent saying it completed a task is never evidence that the task is complete.**

ForgeLoop only marks work complete when the appropriate verification gates pass.

That should become the project's central design principle.

---

# 2. Web app, not desktop

I'd make the primary ForgeLoop product a **React web application**.

You've already demonstrated desktop/local agent tooling. This project gives you an opportunity to demonstrate distributed enterprise architecture instead.

The web application is the **control plane**.

It does not need access to the developer's filesystem and it should not normally execute arbitrary repository code.

Its responsibilities are:

```text
Authentication
Organizations
Repositories
Feature specifications
Runs
Agent configuration
Task graphs
Runner management
MCP configuration
Provider/model configuration
Rules
Events
Verification results
Artifacts
Usage metrics
Audit logs
```

The expensive and potentially dangerous work happens elsewhere.

---

# 3. ForgeLoop Runner

Users install a ForgeLoop runner on their workstation, development server, CI machine, or cloud VM.

For example:

```bash
docker run \
  -e FORGELOOP_TOKEN=... \
  -v /var/run/docker.sock:/var/run/docker.sock \
  forgeloop/runner
```

The runner establishes an outbound connection to ForgeLoop.

```text
                FORGELOOP CLOUD

          React Web Application
                   │
                   ▼
            Spring Boot API
                   │
       ┌───────────┼────────────┐
       ▼           ▼            ▼
   PostgreSQL    Queue      Object Storage
                   │
                   │ task
                   ▼
──────────────────────────────────────────

             CUSTOMER MACHINE

             ForgeLoop Runner
                   │
        ┌──────────┼───────────┐
        ▼          ▼           ▼
     Agent A    Agent B     Agent C
        │          │           │
        ▼          ▼           ▼
   Worktree    Worktree     Worktree
        │          │           │
        └──────────┼───────────┘
                   ▼
                 Docker
                   │
            Build / Test / Run
```

This makes ForgeLoop itself dramatically easier to scale.

---

# 4. Technology stack

For this particular project, I'd deliberately use technologies from the job posting.

### Control plane

**Backend**

```text
Java
Spring Boot
Spring Security
GraphQL
PostgreSQL
Redis
Docker
```

**Frontend**

```text
React
TypeScript
GraphQL
Vite or Next.js
Playwright
```

I'd probably use React + Vite rather than introduce Next.js unless there's something you specifically need from Next. The Java service should clearly own the backend.

### Runner

I'd strongly consider writing the runner in **Java as well**.

That forces you to demonstrate production Java rather than allowing Java to be a thin API wrapper around everything interesting.

The runner handles:

```text
Agent orchestration
Git operations
Worktrees
Docker execution
Process management
MCP
Tool permissions
Provider communication
Streaming events
Artifact collection
Verification
```

That's substantial Java engineering.

---

# 5. The demo application

Don't test ForgeLoop against toy repositories.

Include a legitimate example B2B SaaS application:

### ForgeDesk

A small enterprise support platform with:

```text
Organizations
Users
RBAC
Tickets
Comments
Assignments
Audit logs
Notifications
Admin dashboard
```

Architecture:

```text
React
    │
 GraphQL
    │
Spring Boot
    │
PostgreSQL
```

Containerize the entire application.

ForgeDesk gives ForgeLoop something realistic to modify.

---

# 6. Repository understanding

Before planning anything, ForgeLoop builds a **repository context package**.

It should understand things such as:

```text
Languages
Frameworks
Directory structure
Build system
Test framework
Existing conventions
GraphQL schema
Database structure
Docker services
CI configuration
Architecture rules
Relevant files
```

Do not dump the entire repository into an LLM.

Create targeted context.

For example:

```json
{
  "task": "Implement ticket assignment",
  "backend": {
    "framework": "Spring Boot",
    "relevantFiles": [],
    "architectureRules": []
  },
  "frontend": {
    "framework": "React",
    "relevantFiles": []
  },
  "verification": {
    "backend": "./mvnw test",
    "frontend": "npm test",
    "browser": "npx playwright test"
  }
}
```

This demonstrates their **context-wiring** requirement.

---

# 7. Planning

The planner receives the original specification and repository context.

It returns a structured task graph rather than prose.

Something like:

```text
FEATURE-142

├── TASK-1 GraphQL schema
│
├── TASK-2 Backend assignment service
│   └── depends: TASK-1
│
├── TASK-3 Authorization
│   └── depends: TASK-2
│
├── TASK-4 React assignment UI
│   └── depends: TASK-1
│
├── TASK-5 Backend tests
│   └── depends: TASK-2, TASK-3
│
├── TASK-6 Frontend tests
│   └── depends: TASK-4
│
└── TASK-7 Browser verification
    └── depends: *
```

Now ForgeLoop knows which work can execute concurrently.

---

# 8. Parallel agents

This is one of the most important parts of the project.

Don't implement:

```text
Agent
 ↓
you
 ↓
Agent
 ↓
you
 ↓
Agent
```

Implement actual parallel autonomy.

For example:

```text
             Planner
                │
       ┌────────┼─────────┐
       ▼        ▼         ▼
    Backend   Frontend   Tests
       │        │         │
       │        │         │
      Claude   Codex    Claude
       │        │         │
       └────────┼─────────┘
                ▼
            Integration
```

Each agent gets:

```text
Original specification
Assigned task
Relevant repository context
Architecture rules
Permitted tools
Verification commands
Dependency outputs
Completion requirements
```

Not the entire universe.

---

# 9. Isolated Git worktrees

Every implementation agent gets its own Git worktree.

```text
repo/

worktrees/
    backend-142/
    frontend-142/
    tests-142/
```

That allows agents to operate simultaneously without stomping on each other's changes.

Each produces a commit/diff.

An integration stage combines them.

Conflicts become another machine-readable problem that can be routed to an agent.

---

# 10. MCP

MCP becomes the tool/context layer rather than a buzzword bolted onto the project.

ForgeLoop can expose internal MCP tools like:

```text
repository.get_context
repository.search
git.diff
git.status

spec.get_requirements

build.run
test.backend
test.frontend
test.integration

docker.start
docker.logs
docker.health

browser.navigate
browser.screenshot
browser.run_tests

verification.submit
verification.get_failures
```

You can also connect external MCP servers:

```text
GitHub
Linear
Sentry
Documentation
Database tools
```

But there's an important architectural distinction.

### Local MCP

Anything requiring repository/system access executes on the runner:

```text
Filesystem
Git
Docker
Terminal
Browser
Tests
```

### Remote MCP

Network services can be contacted remotely:

```text
GitHub
Linear
Documentation
Sentry
```

ForgeLoop Cloud stores configuration and permissions.

The runner performs execution.

---

# 11. Tool permissions

Make tool access explicit.

A frontend agent doesn't automatically get arbitrary shell access.

For example:

```yaml
frontend-agent:
  tools:
    - repository.read
    - repository.write_frontend
    - test.frontend
    - browser.run

  denied:
    - database.production
    - secrets.read
    - docker.privileged
```

This demonstrates enterprise-oriented agent design.

---

# 12. Provider abstraction

Don't make ForgeLoop a Claude wrapper.

Define a provider interface:

```text
ModelProvider

├── AnthropicProvider
├── OpenAIProvider
├── GeminiProvider
├── OllamaProvider
└── OpenAICompatibleProvider
```

An agent configuration could say:

```yaml
role: backend-engineer

provider: anthropic
model: <configured model>

context_budget: 100000
max_attempts: 4

tools:
  - git
  - filesystem
  - build
  - tests
```

Users should be able to change models without changing the harness.

---

# 13. BYOK

For the initial release, use **bring your own key**.

Prefer credentials stored on the runner:

```text
ANTHROPIC_API_KEY
OPENAI_API_KEY
GEMINI_API_KEY
```

Then:

```text
ForgeLoop Cloud
      │
      │ "Execute TASK-829"
      ▼
ForgeLoop Runner
      │
      ├────────► Anthropic
      │
      ├────────► OpenAI
      │
      └────────► Gemini
```

Your server never sees the provider credential.

That's both simpler and a compelling security property.

Later you can offer encrypted managed credentials.

---

# 14. Closed-loop verification

This should be the project's strongest feature.

Suppose the backend agent claims it's finished.

ForgeLoop independently runs:

```bash
./mvnw compile
./mvnw test
docker compose build
docker compose up
```

Then maybe:

```text
Compile                PASS
Unit tests             PASS
Integration tests      FAIL
```

The agent does **not** get:

> Tests failed. Fix them.

It gets structured failure context:

```text
TASK-483

Verification failure:
TicketAssignmentAuthorizationTest

Expected:
403

Actual:
200

Original requirement:
Users cannot assign tickets to users belonging
to another organization.

Relevant diff:
...

Relevant logs:
...
```

The repair agent modifies the implementation.

Then ForgeLoop reruns verification.

```text
Implementation
     ↓
Verification
     ↓
 FAIL
     ↓
Failure analysis
     ↓
Repair
     ↓
Verification
     ↓
 FAIL
     ↓
Repair
     ↓
Verification
     ↓
 PASS
```

Set a maximum repair budget.

For example:

```text
maxRepairAttempts = 4
```

After that, ForgeLoop stops and requests human intervention.

That's a real closed loop rather than an infinite agent wandering around a repository.

---

# 15. Independent tests

Here's another feature that would make this impressive.

Don't only let the implementation agent write its own tests.

Have a **verification agent independently derive tests from the specification**.

Implementation agent sees:

```text
Feature spec
Repository
```

Verification agent sees:

```text
Feature spec
Acceptance criteria
Public interfaces
```

It creates tests independently.

That makes it harder for an implementation agent to satisfy its own incorrect interpretation.

---

# 16. Browser verification

For frontend changes, actually launch the application.

Use Playwright.

```text
Start containers
      ↓
Wait for health checks
      ↓
Open Chromium
      ↓
Login
      ↓
Navigate to ticket
      ↓
Assign user
      ↓
Verify UI
      ↓
Reload
      ↓
Verify persistence
      ↓
Attempt unauthorized assignment
      ↓
Verify rejection
```

Capture screenshots.

Now your evidence bundle includes actual visual proof.

---

# 17. Evidence-backed completion

Every run should produce an immutable-ish evidence bundle.

```text
FEATURE-142/

specification.json

plan/
    task-graph.json

agents/
    planner.json
    backend.json
    frontend.json
    verification.json

diffs/
    backend.patch
    frontend.patch

verification/
    compilation.txt
    backend-tests.xml
    frontend-tests.json
    integration-tests.xml
    playwright.json
    container-health.json

screenshots/
    assignment-before.png
    assignment-after.png

review/
    spec-review.json
    security-review.json

metrics/
    tokens.json
    costs.json
    timing.json

final-report.md
```

That concept also fits naturally with the evidence-trail thinking you've already been using in ResearchOS.

---

# 18. Final review

Before opening the PR, give a separate review agent:

```text
Original specification
Acceptance criteria
Final diff
Verification evidence
Architecture rules
```

Ask it to evaluate requirements individually.

Not:

```text
Looks good.
```

Instead:

```text
Requirement                              Evidence

Assignment mutation                     PASS
Organization validation                 PASS
RBAC enforcement                        PASS
Cross-org rejection                     PASS
React assignment control                PASS
Optimistic UI                           PASS
Audit event                             PASS
Unit tests                              PASS
Browser verification                    PASS
```

Only then can the run become:

**READY FOR REVIEW**

Not "done."

The human still controls merging.

---

# 19. Dashboard

The UI should feel like mission control, not ChatGPT.

Avoid making the main interface a giant chat window.

Something more like:

```text
┌───────────────────────────────────────────────────────────────┐
│ FORGELOOP                         FEATURE-142       ● RUNNING │
├────────────┬──────────────────────────────────────────────────┤
│ Dashboard  │ Add Ticket Assignment                            │
│ Runs       │                                                  │
│ Repos      │       ┌──────────┐                               │
│ Agents     │       │ Planner  │ ✓ 42s                         │
│ Harnesses  │       └────┬─────┘                               │
│ MCP        │            │                                     │
│ Runners    │       ┌────┴─────────────┐                       │
│ Models     │       ▼                  ▼                       │
│ Rules      │   Backend             Frontend                   │
│ Metrics    │   ● Running           ✓ Complete                 │
│            │       │                  │                       │
│            │       └────────┬─────────┘                       │
│            │                ▼                                 │
│            │          Verification                            │
│            │                                                  │
│            │ Backend tests       ████████████ 47/47           │
│            │ Frontend tests      ████████████ 31/31           │
│            │ Browser             ████████░░░░  6/8            │
│            │                                                  │
│            │ Events                                           │
│            │ 11:47:21  Backend tests passed                   │
│            │ 11:47:23  Containers healthy                    │
│            │ 11:47:25  Chromium started                      │
└────────────┴──────────────────────────────────────────────────┘
```

You can still provide an event/log console, but the central abstraction should be **runs, graphs, agents and evidence**, not conversations.

---

# 20. Harnesses

This could eventually become one of the coolest parts.

Allow reusable harness definitions.

For example:

### Full-Stack Feature Harness

```text
Plan
  ↓
Backend ───┐
Frontend ──┼── parallel
Tests ─────┘
  ↓
Integrate
  ↓
Build
  ↓
Test
  ↓
Browser
  ↓
Review
```

### Bug Fix Harness

```text
Reproduce
   ↓
Write failing test
   ↓
Diagnose
   ↓
Implement
   ↓
Verify original reproduction
   ↓
Regression suite
```

### Security Fix Harness

```text
Analyze
  ↓
Reproduce vulnerability
  ↓
Implement
  ↓
Security tests
  ↓
Regression
  ↓
Review
```

Harnesses become reusable organizational knowledge.

That directly demonstrates:

> "each class of work needs less of you than the last."

---

# 21. Rules and organizational knowledge

Organizations can define rules such as:

```text
All GraphQL mutations require authorization.

Never expose database entities directly through GraphQL.

Every API change requires integration tests.

All React mutations need error states.

Never modify database migrations after release.

All UI changes require Playwright verification.
```

ForgeLoop automatically injects relevant rules into agents.

Now knowledge isn't trapped in prompts or someone's head.

It becomes part of the harness.

---

# 22. Scaling architecture

This is where the runner architecture pays off.

Your cloud isn't doing this:

```text
10,000 users
     ×
4 agents
     ×
Docker
     ×
Chromium
     ×
Java builds

= goodbye server
```

Instead:

```text
                     ForgeLoop Cloud

                      Load Balancer
                           │
                ┌──────────┼──────────┐
                ▼          ▼          ▼
              API 1      API 2      API 3
                │          │          │
                └──────────┼──────────┘
                           │
                     PostgreSQL
                           │
                      Queue/Event Bus
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼

         Customer A    Customer B    Customer C
           Runner        Runner        Runner
              │            │            │
           Docker       Docker       Docker
              │            │            │
           Models       Models       Models
```

Your infrastructure primarily handles coordination.

The expensive compute is distributed.

---

# 23. Runner capabilities

A runner registers capabilities:

```json
{
  "runner": "jared-desktop",
  "os": "linux",
  "cpu": 24,
  "memory": 32,
  "docker": true,
  "browser": true,
  "java": true,
  "node": true,
  "gpu": true,
  "localModels": true
}
```

The scheduler can eventually choose an appropriate runner.

For example:

```text
Java task → runner with JDK

Browser task → Playwright-capable runner

Local LLM task → GPU runner
```

Now you're demonstrating distributed scheduling too.

---

# 24. Cloud runners later

Eventually you could offer:

**ForgeLoop Managed Runners**

```text
Task
 ↓
Provision ephemeral VM/container
 ↓
Clone repository
 ↓
Inject short-lived secrets
 ↓
Execute
 ↓
Upload evidence
 ↓
Destroy environment
```

That's where ForgeLoop could eventually charge for compute.

Don't build this for v1.

Self-hosted runners are sufficient and arguably make the architecture more interesting for your application.

---

# 25. Security model

Because this is supposed to look enterprise-grade, explicitly design around:

**Isolation:** Each run gets isolated worktrees and execution environments.

**Secrets:** Provider credentials can stay on the runner.

**Least privilege:** Agents receive only required tools.

**Auditability:** Every tool invocation is recorded.

**Human boundaries:** Dangerous actions can require approval.

**Resource limits:** Agent processes have CPU/memory/time limits.

**Network policies:** Execution environments can have configurable network access.

**Repository boundaries:** Agents cannot wander outside their assigned workspace.

**Destructive operations:** Explicitly blocked or approval-gated.

You don't have to build Kubernetes-level isolation. You do need to show that you've thought through the trust boundary.

---

# 26. Observability

Track everything.

A run should tell you:

```text
Elapsed time
Agent execution time
Queue time
Model
Provider
Input tokens
Output tokens
Cost
Tool calls
Build attempts
Repair attempts
Verification failures
Human interventions
Acceptance criteria
Final status
```

Then aggregate it.

For example:

```text
Last 30 days

Runs                         143
Successful                   126
Human intervention            17
Zero-intervention rate       88%
First-pass verification      61%
After autonomous repair      88%
Median feature time          18m
Median repair loops          1.3
```

That's much more meaningful than claiming your harness "increases productivity."

---

# 27. Model experiments

This also gives you a legitimate model evaluation platform.

Run identical feature specifications with different configurations.

For example:

```text
                Config A   Config B   Config C

Success            8/10       9/10       7/10
First pass         5/10       7/10       6/10
Avg repairs         1.8        0.9        1.2
Avg time            22m        17m        14m
Avg cost          $5.82      $7.41      $2.96
```

Don't hardcode conclusions like "model X is best."

Show empirical results.

That demonstrates exactly what they mean by **model judgment**.

---

# 28. CI/CD

Dogfood ForgeLoop.

Its own repository should have:

```text
GitHub Actions

Java compile
Java tests
Frontend tests
TypeScript checks
Docker build
Integration tests
Playwright
Security/dependency checks
```

And deploy the web control plane automatically.

Even better, eventually have ForgeLoop analyze its own PRs.

---

# 29. The first complete demo

Do **one scenario exceptionally well** before adding everything else.

Create ForgeDesk without ticket assignment.

Then submit the specification.

ForgeLoop should visibly:

1. Analyze the repository.
2. Create acceptance criteria.
3. Produce the task DAG.
4. Launch backend/frontend/test agents concurrently.
5. Give each an isolated worktree.
6. Generate changes.
7. Integrate them.
8. Compile the Java application.
9. Run backend tests.
10. Run frontend tests.
11. Start the Docker application.
12. Run Playwright.
13. Deliberately encounter at least one genuine failure.
14. Route that failure into an autonomous repair loop.
15. Rerun verification.
16. Verify every acceptance criterion.
17. Produce screenshots/logs/test evidence.
18. Have the independent reviewer inspect everything.
19. Generate a final report.
20. Open a GitHub PR.

The demo ends with something like:

```text
FEATURE-142
Ticket Assignment

STATUS
READY FOR HUMAN REVIEW

Agents                         5
Parallel workers               3
Human interventions            0
Repair loops                   2

VERIFICATION

Java compilation           PASS
Backend tests          47 / 47
Frontend tests         31 / 31
Integration tests      18 / 18
Browser scenarios        8 / 8
Container health           PASS
Security checks            PASS
Requirements              9 / 9

Elapsed                    14m
Model cost               $4.21

View Evidence
View Diff
View Agent Trace
Open Pull Request
```

That is your MVP.

---

# 30. Build order

I would resist building all the cool infrastructure simultaneously. Build it vertically.

**Phase 1: Foundation.** Create ForgeDesk, Spring Boot API, React control plane, Postgres, authentication, repository registration and a basic runner that can connect to the control plane.

**Phase 2: One autonomous agent.** Send a task to a runner, create a worktree, invoke one model, let it edit files, run verification and return artifacts.

**Phase 3: Closed loops.** Implement structured verification failures, automatic repair, attempt budgets and evidence collection. At this point the project already demonstrates something meaningful.

**Phase 4: Multi-agent DAG.** Add planning, dependencies, parallel workers, isolated worktrees and integration.

**Phase 5: Browser verification.** Docker Compose + Playwright + screenshots + frontend verification.

**Phase 6: MCP and permissions.** Turn your internal capabilities into explicit tools and add permission boundaries.

**Phase 7: Model/provider abstraction.** Add multiple providers, BYOK and model configuration.

**Phase 8: Polish.** Metrics, dashboards, audit trails, final review, GitHub PR creation and excellent documentation.

Don't start with billing, cloud runners, Kubernetes, ten providers, a marketplace or twenty MCP integrations.

---

# 31. How this maps to the job

The resulting portfolio project makes the connection extremely obvious:

| Their requirement      | ForgeLoop                          |
| ---------------------- | ---------------------------------- |
| Java                   | Spring Boot control plane + runner |
| ReactJS                | Mission-control dashboard          |
| GraphQL                | API + ForgeDesk                    |
| Containerized services | Docker execution environments      |
| Senior full-stack      | Entire system                      |
| Build the harness      | Literally the product              |
| Multiple agents        | DAG scheduler                      |
| Parallel agents        | Concurrent worktrees               |
| Context wiring         | Repository context builder         |
| Closed loops           | Verify → repair → verify           |
| Agent autonomy         | Complete bounded tasks             |
| Product taste          | Web control plane                  |
| Model judgment         | Provider experiments               |
| Advanced AI tooling    | Agent orchestration                |
| MCP                    | Tool/context layer                 |
| Enterprise software    | RBAC/security/auditing             |
| CI/CD                  | Automated pipeline                 |
| End-to-end ownership   | Spec → PR                          |
| Quality                | Objective completion gates         |
| Internal tooling       | Reusable harness definitions       |
| Multiplication         | Rules/harnesses reused across runs |

It's almost a **reference implementation of their job description** without simply cloning whatever product they actually make.

