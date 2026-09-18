# ForgeLoop orchestration core

This Java module is deliberately provider-agnostic. `ModelProvider` adapts Claude, OpenAI, Gemini, or a local runtime; it returns normalized `AgentRun` metadata including model selection rationale, token use, cost, and outcome. The orchestrator owns state transitions and never delegates final completion to a provider.

`EvidenceWriter` is intentionally an interface: use a filesystem implementation in local/Docker runs and an object-store implementation in CI. Every transition should emit an append-only event before it becomes externally visible.

