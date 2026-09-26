import {
  FormEvent,
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import { createRoot } from "react-dom/client";
import { BarChart3, BookOpen, GitBranch, ListChecks, SlidersHorizontal } from "lucide-react";
import {
  approveFeatureRun,
  acknowledgeEscalation,
  cancelFeatureRun,
  configureOrganizationPolicy,
  createHarnessDefinition,
  createLocalMcpConfiguration,
  loadOperator,
  loadRunAnalytics,
  loadPlatformConfiguration,
  loadRepositoryConnections,
  loadRun,
  loadRunOperations,
  loadRuns,
  retryFeatureTask,
  resolveEscalation,
  submitFeature,
  type FeatureRun,
  type OperatorSession,
  type RepositoryConnection,
  type RunOperations,
  type RunAnalytics,
  type PlatformConfiguration,
} from "./api";
import favicon from "./assets/favicon.png";
import MarkdownContent from "./MarkdownContent";
import UserGuidePage from "./UserGuidePage";
import LandingPage from "./LandingPage";
import "./styles.css";

const terminal = new Set(["COMPLETE", "CANCELLED", "FAILED", "REJECTED"]);
const retryable = new Set(["FAILED", "HELD", "RETRYABLE_FAILURE"]);
const money = (micros: number) => `$${(micros / 1_000_000).toFixed(2)}`;
const stamp = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));

function RepositoryPage({ items }: { items: RepositoryConnection[] }) {
  return (
    <>
      <section className="hero">
        <p className="eyebrow">Repository authorization</p>
        <h1>Connect policy, not source code.</h1>
        <p>
          Install the ForgeLoop GitHub App and choose the repositories it may
          access. GitHub supplies the installation identity; source execution
          remains on an authorized runner.
        </p>
      </section>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>Connected repositories</h2>
            <p>
              Issue intake and verification are bound to the persisted policy
              revision.
            </p>
          </div>
          <a className="primary" href="/api/github/app/install">
            Install GitHub App
          </a>
        </div>
        {items.length ? (
          items.map((item) => (
            <div className="item" key={item.id}>
              <div>
                <b>{item.repository}</b>
                <small>
                  {item.defaultBranch} · {item.issueLabel} ·{" "}
                  {item.requiredGates.join(", ")}
                </small>
              </div>
              <span className="status complete">
                Policy v{item.policyRevision}
              </span>
            </div>
          ))
        ) : (
          <p className="empty">No repositories are authorized yet.</p>
        )}
      </section>
    </>
  );
}

function AnalyticsPage({analytics}:{analytics?:RunAnalytics}){
  if(!analytics)return <p className="loading">Loading analytics…</p>;
  const table=(title:string,items:RunAnalytics["modelComparisons"])=><section className="panel"><h2>{title}</h2>{items.length?items.map(item=><div className="item" key={item.name}><div><b>{item.name}</b><small>{item.runCount} runs · {item.requestCount} requests · {item.successfulRequests} succeeded · {(item.inputTokens+item.outputTokens).toLocaleString()} tokens</small></div><span>{money(item.knownCostMicros)}</span></div>):<p className="empty">No comparison data recorded yet.</p>}</section>;
  return <><section className="hero"><p className="eyebrow">Measured delivery</p><h1>Run analytics</h1><p>Tenant-scoped facts from persisted provider telemetry—never fabricated estimates.</p></section><section className="metrics analytics-metrics"><article><b>{analytics.totalRuns}</b><span>Total runs</span></article><article><b>{analytics.activeRuns}</b><span>Active runs</span></article><article><b>{analytics.deliveredRuns}</b><span>Delivered runs</span></article><article><b>{analytics.providerRequests}</b><span>Provider requests</span></article><article><b>{money(analytics.knownCostMicros)}</b><span>Known cost · {Math.round(analytics.costCoverage*100)}% coverage</span></article></section><div className="two-column">{table("Model comparison",analytics.modelComparisons)}{table("Harness comparison",analytics.harnessComparisons)}</div></>;
}

function NewRun({
  repositories,
  onAdded,
}: {
  repositories: RepositoryConnection[];
  onAdded: (run: FeatureRun) => void;
}) {
  const [form, setForm] = useState({
    repository: "",
    sourceRef: "",
    title: "",
    specification: "",
    budgetUsd: 25,
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      onAdded(await submitFeature(form));
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Unable to create run",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="panel form" onSubmit={(event) => void submit(event)}>
      <h2>Start a delivery run</h2>
      <div className="form-grid">
        <label>
          Authorized repository
          <select
            aria-label="Authorized repository"
            required
            value={form.repository}
            onChange={(event) =>
              setForm({ ...form, repository: event.target.value })
            }
          >
            <option value="">Select repository</option>
            {repositories
              .filter((item) => item.enabled)
              .map((item) => (
                <option key={item.id}>{item.repository}</option>
              ))}
          </select>
        </label>
        <label>
          Issue or source reference
          <input
            aria-label="Issue or source reference"
            required
            value={form.sourceRef}
            onChange={(event) =>
              setForm({ ...form, sourceRef: event.target.value })
            }
          />
        </label>
      </div>
      <label>
        Title
        <input
          aria-label="Title"
          required
          value={form.title}
          onChange={(event) => setForm({ ...form, title: event.target.value })}
        />
      </label>
      <label>
        Acceptance-focused specification
        <textarea
          aria-label="Specification"
          required
          value={form.specification}
          onChange={(event) =>
            setForm({ ...form, specification: event.target.value })
          }
        />
      </label>
      <label>
        Maximum spend (USD)
        <input
          aria-label="Maximum spend"
          type="number"
          min="1"
          step="1"
          value={form.budgetUsd}
          onChange={(event) =>
            setForm({ ...form, budgetUsd: Number(event.target.value) })
          }
        />
      </label>
      <button
        className="primary"
        disabled={busy || !repositories.some((item) => item.enabled)}
      >
        {busy ? "Submitting…" : "Create delivery run"}
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}

function RunDetail({
  run,
  operations,
  operator,
  onRefresh,
}: {
  run: FeatureRun;
  operations: RunOperations;
  operator: OperatorSession;
  onRefresh: () => Promise<void>;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const budget = Math.round(run.budgetUsd * 1_000_000);
  const progress = Math.min(
    100,
    budget ? (run.spentCostMicros / budget) * 100 : 0,
  );
  const canOperate = operator.role !== "VIEWER";
  const visibleRunState = operations.publication?.pullRequestState === "MERGED" || run.publication?.pullRequestState === "MERGED" ? "COMPLETE" : run.state;
  const retryableTask = run.tasks.find((task) => retryable.has(task.state));
  async function action(label: string, callback: () => Promise<unknown>) {
    setBusy(label);
    setError("");
    try {
      await callback();
      await onRefresh();
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Operator action failed",
      );
    } finally {
      setBusy("");
    }
  }
  return (
    <div className="detail-stack">
      <section className="panel run-head">
        <div>
          <p className="eyebrow">
            {run.repository} · {run.sourceRef}
          </p>
          <h1>{run.title}</h1>
          <MarkdownContent content={run.specification} />
        </div>
        <span className={`status ${visibleRunState.toLowerCase()}`}>
          {visibleRunState.replaceAll("_", " ")}
        </span>
      </section>
      <section className="metrics">
        <article>
          <b>
            {run.tasks.filter((task) => task.state === "VERIFIED").length}/
            {run.tasks.length}
          </b>
          <span>Tasks verified</span>
        </article>
        <article>
          <b>
            {
              run.gates.filter((gate) =>
                ["PASSED", "SKIPPED_BY_POLICY"].includes(gate.state),
              ).length
            }
            /{run.gates.length}
          </b>
          <span>Gates satisfied</span>
        </article>
        <article>
          <b>
            {run.tasks
              .reduce(
                (sum, task) =>
                  sum +
                  task.providerAttempts.reduce(
                    (total, attempt) =>
                      total + attempt.inputTokens + attempt.outputTokens,
                    0,
                  ),
                0,
              )
              .toLocaleString()}
          </b>
          <span>Tokens used</span>
        </article>
        <article>
          <b>{money(run.spentCostMicros)}</b>
          <span>of ${run.budgetUsd.toFixed(2)}</span>
        </article>
      </section>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>Budget & controls</h2>
            <p>
              Actions are role-checked, confirmed, and written to the audit
              ledger.
            </p>
          </div>
          <div className="actions">
            {["FAILED", "BLOCKED"].includes(run.state) && retryableTask && canOperate && (
              <button
                className="secondary"
                disabled={!!busy}
                onClick={() => {
                  const reason = window.prompt("Reason for retrying this failed run");
                  if (reason) void action("retry-run", () => retryFeatureTask(retryableTask.id, reason));
                }}
              >
                {busy === "retry-run" ? "Retrying…" : "Retry run"}
              </button>
            )}
            {run.state === "READY_FOR_REVIEW" &&
              !run.approved &&
              operator.role === "ADMIN" && (
                <button
                  className="primary compact"
                  disabled={!!busy}
                  onClick={() =>
                    window.confirm(
                      "Approve this verified run for GitHub delivery?",
                    ) && void action("approve", () => approveFeatureRun(run.id))
                  }
                >
                  Approve release
                </button>
              )}
            {!terminal.has(run.state) && !run.approved && canOperate && (
              <button
                className="danger"
                disabled={!!busy}
                onClick={() =>
                  window.confirm(
                    "Cancel this run and hold all active tasks?",
                  ) && void action("cancel", () => cancelFeatureRun(run.id))
                }
              >
                Cancel run
              </button>
            )}
            {run.approved && (
              <span className="approval">Approved by {run.approvedBy}</span>
            )}
          </div>
        </div>
        <div className="budget">
          <span style={{ width: `${progress}%` }} />
        </div>
        <small>
          {money(run.spentCostMicros)} used ·{" "}
          {money(Math.max(0, budget - run.spentCostMicros))} remaining · policy
          revision {run.policyRevision}
        </small>
        {error && <p role="alert">{error}</p>}
      </section>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>Task graph & attempts</h2>
            <p>Dependencies, repair loops, provider usage, and owned paths.</p>
          </div>
        </div>
        {run.tasks.map((task) => (
          <article className="task" key={task.id}>
            <div className="task-marker" />
            <div>
              <div className="task-title">
                <b>{task.title}</b>
                <span className={`status ${task.state.toLowerCase()}`}>
                  {task.state.replaceAll("_", " ")}
                </span>
              </div>
              <small>
                {task.executionRole} · {task.planKey} · depends on{" "}
                {task.dependencyKeys.join(", ") || "nothing"}
              </small>
              {task.ownedPaths.length > 0 && (
                <small>Scope: {task.ownedPaths.join(", ")}</small>
              )}
              <div className="attempts">
                {task.providerAttempts.map((attempt) => (
                  <span key={attempt.id}>
                    {attempt.provider}/{attempt.model} · {attempt.outcome} ·{" "}
                    {money(attempt.estimatedCostMicros)}
                  </span>
                ))}
                {task.repairPackages.map((repair) => (
                  <span key={repair.id}>
                    Repair {repair.attempt}: {repair.failureCategory}
                  </span>
                ))}
              </div>
            </div>
            {retryable.has(task.state) && canOperate && (
              <button
                className="secondary"
                disabled={!!busy}
                onClick={() => {
                  const reason = window.prompt(
                    "Reason for granting one additional repair attempt",
                  );
                  if (reason)
                    void action(`retry-${task.id}`, () =>
                      retryFeatureTask(task.id, reason),
                    );
                }}
              >
                Retry
              </button>
            )}
          </article>
        ))}
      </section>
      <div className="two-column">
        <section className="panel">
          <h2>Verification gates</h2>
          {run.gates.map((gate) => (
            <div className="item" key={gate.id}>
              <div>
                <b>{gate.name}</b>
                <small>
                  {gate.kind} · {gate.networkPolicy} network ·{" "}
                  {gate.timeoutSeconds}s
                </small>
              </div>
              <span className={`status ${gate.state.toLowerCase()}`}>
                {gate.state.replaceAll("_", " ")}
              </span>
            </div>
          ))}
        </section>
        <section className="panel">
          <h2>Acceptance criteria</h2>
          {run.criteria.map((criterion) => (
            <div className="item" key={criterion.id}>
              <div>
                <b>{criterion.statement}</b>
              </div>
              <span
                className={`status ${criterion.coverageState.toLowerCase()}`}
              >
                {criterion.coverageState}
              </span>
            </div>
          ))}
        </section>
      </div>
      <section className="panel">
        <div className="section-heading"><div><h2>Runner event stream</h2><p>Redacted lease progress refreshes while this run is active.</p></div></div>
        {operations.events.length ? operations.events.map(event=><div className="timeline" key={event.id}><i/><div><b>{event.eventType.replaceAll("_"," ")}</b><small>{event.message} · {stamp(event.occurredAt)}</small></div></div>):<p className="empty">No runner events have been received.</p>}
      </section>
      <section className="panel">
        <div className="section-heading"><div><h2>Human escalation queue</h2><p>Automation stops safely when a configured boundary is reached.</p></div></div>
        {operations.escalations.length ? operations.escalations.map(item=><div className="item" key={item.id}><div><b>{item.reason.replaceAll("_"," ")}</b><small>{item.summary} · {item.severity} · {stamp(item.createdAt)}</small></div><div className="actions"><span className={`status ${item.status.toLowerCase()}`}>{item.status}</span>{canOperate&&item.status==="OPEN"&&<button className="secondary" disabled={!!busy} onClick={()=>void action(`ack-${item.id}`,()=>acknowledgeEscalation(item.id))}>Acknowledge</button>}{canOperate&&item.status!=="RESOLVED"&&<button className="secondary" disabled={!!busy} onClick={()=>void action(`resolve-${item.id}`,()=>resolveEscalation(item.id))}>Resolve</button>}</div></div>):<p className="empty">No human intervention is required.</p>}
      </section>
      <section className="panel">
        <h2>Independent review</h2>
        {operations.reviews.length ? (
          operations.reviews.map((review) => (
            <details className="evidence" key={review.id} open>
              <summary>
                <span>
                  <b>{review.summary}</b>
                  <small>
                    Runner {review.runnerId} · {stamp(review.recordedAt)}
                  </small>
                </span>
                <span
                  className={`status ${review.approved ? "passed" : "failed"}`}
                >
                  {review.approved ? "APPROVED" : "REJECTED"}
                </span>
              </summary>
              {review.criteria.map((criterion) => (
                <div className="item" key={criterion.id}>
                  <div>
                    <b>{criterion.statement}</b>
                    <small>{criterion.evidence}</small>
                  </div>
                  <span
                    className={`status ${criterion.status === "PASS" ? "passed" : "failed"}`}
                  >
                    {criterion.status}
                  </span>
                </div>
              ))}
              <dl>
                <dt>Digest</dt>
                <dd>{review.digest}</dd>
              </dl>
            </details>
          ))
        ) : (
          <p className="empty">No independent review has been recorded yet.</p>
        )}
      </section>
      <section className="panel">
        <h2>Evidence browser & redacted logs</h2>
        {operations.artifacts.length > 0 && <div className="artifact-grid">{operations.artifacts.map(artifact=><a className={`artifact-card ${artifact.artifactType === "SCREENSHOT" ? "screenshot" : ""}`} href={`/api/artifacts/${artifact.id}`} target="_blank" rel="noreferrer" key={artifact.id}>{artifact.artifactType === "SCREENSHOT" && <img src={`/api/artifacts/${artifact.id}`} alt={artifact.displayName}/>}<span><b>{artifact.displayName}</b><small>{artifact.artifactType.replaceAll("_"," ")} · {(artifact.sizeBytes/1024).toFixed(1)} KiB</small><small>SHA-256 {artifact.sha256.slice(0,12)}…</small></span></a>)}</div>}
        {operations.evidence.length ? (
          operations.evidence.map((item) => (
            <details className="evidence" key={item.id}>
              <summary>
                <span>
                  <b>{item.gate}</b>
                  <small>
                    {item.kind} · exit {item.exitCode} ·{" "}
                    {stamp(item.recordedAt)}
                  </small>
                </span>
                <span
                  className={`status ${item.exitCode === 0 && !item.timedOut ? "passed" : "failed"}`}
                >
                  {item.timedOut
                    ? "TIMED OUT"
                    : item.exitCode === 0
                      ? "PASSED"
                      : "FAILED"}
                </span>
              </summary>
              <dl>
                <dt>Image</dt>
                <dd>{item.image}</dd>
                <dt>Command</dt>
                <dd>{item.command.join(" ")}</dd>
                <dt>Digest</dt>
                <dd>{item.digest}</dd>
                {item.artifactReference && (
                  <>
                    <dt>Artifact</dt>
                    <dd>{item.artifactReference}</dd>
                  </>
                )}
              </dl>
              <pre>{item.output}</pre>
            </details>
          ))
        ) : (
          <p className="empty">No runner evidence has been recorded yet.</p>
        )}
      </section>
      <div className="two-column">
        <section className="panel">
          <h2>Audit timeline</h2>
          {operations.audit.length ? (
            operations.audit.map((event) => (
              <div className="timeline" key={event.id}>
                <i />
                <div>
                  <b>{event.action.replaceAll("_", " ")}</b>
                  <small>
                    {event.actor} · {stamp(event.occurredAt)}
                  </small>
                </div>
              </div>
            ))
          ) : (
            <p className="empty">No audit events recorded.</p>
          )}
        </section>
        <section className="panel github-delivery">
          <div className="section-heading github-delivery-heading">
            <div><p className="eyebrow">Publication</p><h2>GitHub delivery</h2></div>
            {operations.publication && (() => {
              const state = operations.publication.mergedAt ? "MERGED" : operations.publication.pullRequestState;
              const stateLabels: Record<string, string> = { OPEN: "PULL REQUEST OPEN", DRAFT: "DRAFT PR", CLOSED: "CLOSED", MERGED: "MERGED", UNKNOWN: "STATUS UNKNOWN", NOT_CREATED: "BRANCH READY" };
              const stateClasses: Record<string, string> = { OPEN: "ready_for_review", DRAFT: "running", CLOSED: "closed", MERGED: "complete", UNKNOWN: "held", NOT_CREATED: "running" };
              return <span className={`status ${stateClasses[state] ?? "held"}`}>{stateLabels[state] ?? state}</span>;
            })()}
          </div>
          {operations.publication ? (
            <>
              <dl className="github-delivery-meta">
                <div><dt>Repository</dt><dd>{operations.publication.repository}</dd></div>
                <div><dt>Branch</dt><dd><code title={operations.publication.branch}>{operations.publication.branch}</code></dd></div>
                <div><dt>Commit</dt><dd><code title={operations.publication.headSha}>{operations.publication.headSha?.slice(0, 12) ?? "Pending"}</code></dd></div>
                <div><dt>Delivery</dt><dd>{operations.publication.mergedAt ? `Merged ${stamp(operations.publication.mergedAt)}` : operations.publication.pullRequestState === "MERGED" ? "Merged on GitHub" : operations.publication.pullRequestState === "CLOSED" ? "Closed on GitHub" : operations.publication.deliveredAt ? `Published ${stamp(operations.publication.deliveredAt)}` : "Awaiting publication"}</dd></div>
                <div><dt>Auto-merge</dt><dd>{operations.publication.mergedAt || operations.publication.pullRequestState === "MERGED" ? "Merged" : operations.publication.autoMergeRequested ? "Requested" : "Not requested"}</dd></div>
                {operations.publication.mergeSha && <div><dt>Merge commit</dt><dd><code title={operations.publication.mergeSha}>{operations.publication.mergeSha.slice(0, 12)}</code></dd></div>}
              </dl>
              {operations.publication.pullRequestNumber && (
                <a
                  className="primary inline"
                  href={`https://github.com/${operations.publication.repository}/pull/${operations.publication.pullRequestNumber}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  View pull request #{operations.publication.pullRequestNumber}
                </a>
              )}
            </>
          ) : (
            <p className="empty">
              A draft pull request is created only after verification and
              explicit approval.
            </p>
          )}
        </section>
      </div>
    </div>
  );
}

type RunFilter = "All" | "Running" | "Succeeded" | "Failed";

function runCategory(state: string): Exclude<RunFilter, "All"> {
  if (["READY_FOR_REVIEW", "COMPLETE"].includes(state)) return "Succeeded";
  if (["BLOCKED", "FAILED", "REJECTED", "CANCELLED"].includes(state)) return "Failed";
  return "Running";
}

function displayedRunState(run: FeatureRun): string {
  return run.publication?.pullRequestState === "MERGED" ? "COMPLETE" : run.state;
}

function runProgress(run: FeatureRun): number {
  if (["READY_FOR_REVIEW", "COMPLETE"].includes(displayedRunState(run))) return 100;
  if (!run.tasks.length) return 0;
  return Math.round((run.tasks.filter((task) => task.state === "VERIFIED").length / run.tasks.length) * 100);
}

function relativeTime(value: string): string {
  const elapsed = Math.max(0, Date.now() - new Date(value).getTime());
  if (!Number.isFinite(elapsed)) return "—";
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function RunsPage({
  repositories,
  runs,
  operator,
  setRuns,
}: {
  repositories: RepositoryConnection[];
  runs: FeatureRun[];
  operator: OperatorSession;
  setRuns: Dispatch<SetStateAction<FeatureRun[]>>;
}) {
  const [selected, setSelected] = useState<string>();
  const [operations, setOperations] = useState<RunOperations>({
    evidence: [],
    reviews: [],
    artifacts: [],
    events: [],
    escalations: [],
    audit: [],
  });
  const [creating, setCreating] = useState(false);
  const [filter, setFilter] = useState<RunFilter>("All");
  const run = runs.find((item) => item.id === selected);
  const filters: RunFilter[] = ["All", "Running", "Succeeded", "Failed"];
  const visibleRuns = runs.filter((item) => filter === "All" || runCategory(displayedRunState(item)) === filter);
  async function refresh() {
    if (!selected) return;
    const [updated, detail] = await Promise.all([
      loadRun(selected),
      loadRunOperations(selected),
    ]);
    setRuns((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    setOperations(detail);
  }
  useEffect(() => {
    if (!selected) return;
    void loadRunOperations(selected).then(setOperations);
    const timer = window.setInterval(() => void refresh(), 5000);
    return () => window.clearInterval(timer);
  }, [selected]);
  if (creating)
    return (
      <>
        <button className="back" onClick={() => setCreating(false)}>
          ← Back to runs
        </button>
        <NewRun
          repositories={repositories}
          onAdded={(added) => {
            setRuns((current) => [added, ...current]);
            setSelected(added.id);
            setCreating(false);
          }}
        />
      </>
    );
  return (
    <>
      <section className="page-title runs-title">
        <div>
          <h1>Runs</h1>
          <p>Turn specifications into working software.</p>
        </div>
        {operator.role !== "VIEWER" && (
          <button className="primary" onClick={() => setCreating(true)}>
            + New Run
          </button>
        )}
      </section>
      <section className="panel runs-dashboard">
        <h2 className="visually-hidden">Intake queue</h2>
        <div className="run-filters" role="group" aria-label="Filter runs">
          {filters.map((item) => (
            <button key={item} className={filter === item ? "active" : ""} aria-pressed={filter === item} onClick={() => setFilter(item)}>
              {item}{" "}<span>{item === "All" ? runs.length : runs.filter((runItem) => runCategory(displayedRunState(runItem)) === item).length}</span>
            </button>
          ))}
        </div>
        {visibleRuns.length ? (
          <div className="runs-table-wrap">
            <table className="runs-table">
              <thead><tr><th scope="col">Run</th><th scope="col">Repository</th><th scope="col">Status</th><th scope="col">Progress</th><th scope="col">Started</th></tr></thead>
              <tbody>
                {visibleRuns.map((item) => {
                  const progressPercent = runProgress(item);
                  return (
                    <tr key={item.id} className={selected === item.id ? "selected" : ""} tabIndex={0} aria-label={`Open run ${item.title}`} onClick={() => setSelected(item.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelected(item.id); } }}>
                      <td><b>{item.sourceRef}</b><small>{item.title}</small></td>
                      <td>{item.repository}</td>
                      <td><span className={`status ${displayedRunState(item).toLowerCase()}`}>{displayedRunState(item).replaceAll("_", " ")}</span></td>
                      <td><div className="run-progress"><span><i style={{ width: `${progressPercent}%` }} /></span><small>{progressPercent}%</small></div></td>
                      <td><time dateTime={item.createdAt}>{relativeTime(item.createdAt)}</time></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : runs.length ? (
          <p className="empty">No {filter.toLowerCase()} runs.</p>
        ) : (
          <p className="empty">GitHub issues carrying the configured intake label will appear here automatically.</p>
        )}
      </section>
      {run && <RunDetail run={run} operations={operations} operator={operator} onRefresh={refresh} />}
    </>
  );
}

function ConfigurationPage({operator}:{operator:OperatorSession}) {
  const [configuration,setConfiguration]=useState<PlatformConfiguration>();
  const [error,setError]=useState("");
  const [busy,setBusy]=useState("");
  const [harness,setHarness]=useState({name:"",description:"",allowedRoles:"PLANNER,BACKEND,FRONTEND,INDEPENDENT_TEST,INTEGRATION,REVIEW,VERIFICATION",defaultAttemptBudget:2});
  const [mcp,setMcp]=useState({name:"",command:"",arguments:"",allowedRoles:"PLANNER,BACKEND,FRONTEND,REVIEW",contextTool:"",toolArguments:"{}"});
  useEffect(()=>{void loadPlatformConfiguration().then(setConfiguration).catch(reason=>setError(reason instanceof Error?reason.message:"Unable to load configuration"));},[]);
  const admin=operator.role==="ADMIN";
  const csv=(value:string)=>value.split(",").map(item=>item.trim()).filter(Boolean);
  async function perform(label:string,work:()=>Promise<unknown>){setBusy(label);setError("");try{await work();setConfiguration(await loadPlatformConfiguration());}catch(reason){setError(reason instanceof Error?reason.message:"Configuration update failed");}finally{setBusy("");}}
  if(!configuration)return <p className="loading">Loading execution policy…</p>;
  return <>
    <section className="page-title"><div><p className="eyebrow">Execution governance</p><h1>Harness & policy</h1><p>Reusable delivery rules and runner-local context routes for this organization.</p></div><span className="revision">Policy revision {configuration.policy.revision}</span></section>
    {error&&<p role="alert">{error}</p>}
    <section className="panel policy-card"><div><p className="eyebrow">Organization boundary</p><h2>Execution policy</h2><p>Hard limits applied before work enters the runner queue.</p></div><form onSubmit={event=>{event.preventDefault();const form=new FormData(event.currentTarget);void perform("policy",()=>configureOrganizationPolicy({maxRunBudgetUsd:Number(form.get("budget")),maxParallelTasks:Number(form.get("parallel")),allowedProviders:csv(String(form.get("providers"))),requireHumanApproval:form.get("approval")==="on",autoMergeEnabled:form.get("autoMerge")==="on"}));}}><label>Maximum run budget<input name="budget" type="number" min="1" step="1" defaultValue={configuration.policy.maxRunBudgetUsd} disabled={!admin}/></label><label>Parallel task ceiling<input name="parallel" type="number" min="1" max="16" defaultValue={configuration.policy.maxParallelTasks} disabled={!admin}/></label><label>Allowed providers<input name="providers" defaultValue={configuration.policy.allowedProviders.join(", ")} disabled={!admin}/></label><label className="check"><input name="approval" type="checkbox" defaultChecked={configuration.policy.requireHumanApproval} disabled={!admin}/> Require human approval before delivery</label><label className="check"><input name="autoMerge" type="checkbox" defaultChecked={configuration.policy.autoMergeEnabled} disabled={!admin}/> Auto-merge the verified commit after all GitHub checks pass</label>{admin&&<button className="primary compact" disabled={!!busy}>Save policy</button>}</form></section>
    <div className="config-columns"><section className="panel"><div className="section-heading"><div><p className="eyebrow">Reusable orchestration</p><h2>Harness definitions</h2></div><span className="count">{configuration.harnesses.length}</span></div>{configuration.harnesses.map(item=><article className="definition" key={item.id}><div><b>{item.name}</b><span className={`status ${item.enabled?"complete":"failed"}`}>{item.enabled?"ACTIVE":"DISABLED"}</span></div><p>{item.description}</p><small>{item.allowedRoles.join(" · ")} · {item.defaultAttemptBudget} attempts · v{item.revision}</small></article>)}{admin&&<form className="subform" onSubmit={event=>{event.preventDefault();void perform("harness",()=>createHarnessDefinition({...harness,allowedRoles:csv(harness.allowedRoles)}));}}><h3>Add harness</h3><input aria-label="Harness name" placeholder="BUG_FIX" value={harness.name} onChange={event=>setHarness({...harness,name:event.target.value.toUpperCase()})} required/><textarea aria-label="Harness description" placeholder="Describe the bounded workflow" value={harness.description} onChange={event=>setHarness({...harness,description:event.target.value})} required/><input aria-label="Harness roles" value={harness.allowedRoles} onChange={event=>setHarness({...harness,allowedRoles:event.target.value})} required/><input aria-label="Default attempt budget" type="number" min="1" max="10" value={harness.defaultAttemptBudget} onChange={event=>setHarness({...harness,defaultAttemptBudget:Number(event.target.value)})}/><button className="secondary" disabled={!!busy}>Create harness</button></form>}</section>
    <section className="panel"><div className="section-heading"><div><p className="eyebrow">Runner-local context</p><h2>MCP routes</h2></div><span className="count">{configuration.mcp.length}</span></div>{configuration.mcp.length?configuration.mcp.map(item=><article className="definition" key={item.id}><div><b>{item.name}</b><span className={`status ${item.enabled?"complete":"failed"}`}>{item.enabled?"ROUTED":"DISABLED"}</span></div><p><code>{item.command} {item.arguments.join(" ")}</code></p><small>{item.contextTool} · {item.allowedRoles.join(" · ")} · v{item.revision}</small></article>):<p className="empty">No local MCP context routes configured.</p>}{admin&&<form className="subform" onSubmit={event=>{event.preventDefault();try{JSON.parse(mcp.toolArguments);}catch{setError("MCP tool arguments must be valid JSON");return;}void perform("mcp",()=>createLocalMcpConfiguration({...mcp,arguments:csv(mcp.arguments),allowedRoles:csv(mcp.allowedRoles)}));}}><h3>Add local route</h3><input aria-label="MCP route name" placeholder="repository-context" value={mcp.name} onChange={event=>setMcp({...mcp,name:event.target.value})} required/><input aria-label="MCP command" placeholder="node" value={mcp.command} onChange={event=>setMcp({...mcp,command:event.target.value})} required/><input aria-label="MCP command arguments" placeholder="server.js, --stdio" value={mcp.arguments} onChange={event=>setMcp({...mcp,arguments:event.target.value})}/><input aria-label="MCP allowed roles" value={mcp.allowedRoles} onChange={event=>setMcp({...mcp,allowedRoles:event.target.value})} required/><input aria-label="MCP context tool" placeholder="repository.get_context" value={mcp.contextTool} onChange={event=>setMcp({...mcp,contextTool:event.target.value})} required/><textarea aria-label="MCP tool arguments" value={mcp.toolArguments} onChange={event=>setMcp({...mcp,toolArguments:event.target.value})} required/><button className="secondary" disabled={!!busy}>Create route</button></form>}</section></div>
  </>;
}

function App() {
  const [page, setPage] = useState<"Runs" | "Repositories" | "Analytics" | "Configuration" | "Guide">("Runs");
  const [repositories, setRepositories] = useState<RepositoryConnection[]>([]);
  const [runs, setRuns] = useState<FeatureRun[]>([]);
  const [operator, setOperator] = useState<OperatorSession>();
  const [analytics,setAnalytics]=useState<RunAnalytics>();
  const [error, setError] = useState("");
  useEffect(() => {
    void Promise.all([loadRepositoryConnections(), loadRuns(), loadOperator(),loadRunAnalytics()])
      .then(([connected, loaded, current,metrics]) => {
        setRepositories(connected);
        setRuns(loaded);
        setOperator(current);
        setAnalytics(metrics);
      })
      .catch((reason) =>
        setError(
          reason instanceof Error ? reason.message : "Unable to load ForgeLoop",
        ),
      );
  }, []);
  const summary = useMemo(
    () => `${runs.filter((run) => !terminal.has(run.state)).length} active`,
    [runs],
  );
  return (
    <main>
      <header>
        <div>
          <a className="brand" href="/">
            <img src={favicon} alt="" />
            <strong>ForgeLoop</strong>
          </a>
          <span>Evidence-backed software delivery</span>
        </div>
        <div className="session">
          <i />
          {summary}
          {operator && (
            <span>
              {operator.role.toLowerCase()} · {operator.organizationId}
            </span>
          )}
          <a className="logout" href="/logout">Sign out</a>
        </div>
      </header>
      <div className="shell">
        <aside>
          <nav aria-label="ForgeLoop navigation">
            <button
              className={page === "Runs" ? "active" : ""}
              onClick={() => setPage("Runs")}
            >
              <span className="nav-icon" aria-hidden="true"><ListChecks size={17} strokeWidth={1.9} /></span> Runs
            </button>
            <button className={page === "Analytics" ? "active" : ""} onClick={() => setPage("Analytics")}><span className="nav-icon" aria-hidden="true"><BarChart3 size={17} strokeWidth={1.9} /></span> Analytics</button>
            <button
              className={page === "Repositories" ? "active" : ""}
              onClick={() => setPage("Repositories")}
            >
              <span className="nav-icon" aria-hidden="true"><GitBranch size={17} strokeWidth={1.9} /></span> Repositories
            </button>
            <button className={page === "Configuration" ? "active" : ""} onClick={() => setPage("Configuration")}><span className="nav-icon" aria-hidden="true"><SlidersHorizontal size={17} strokeWidth={1.9} /></span> Harness &amp; policy</button>
            <button className={page === "Guide" ? "active" : ""} onClick={() => setPage("Guide")}><span className="nav-icon" aria-hidden="true"><BookOpen size={17} strokeWidth={1.9} /></span> User guide</button>
          </nav>
          <p className="sidebar-note">
            Repository code and commands execute only on an enrolled customer
            runner.
          </p>
        </aside>
        <div className="content">
          {error ? (
            <p role="alert">{error}</p>
          ) : !operator ? (
            <p className="loading">Loading operator workspace…</p>
          ) : page === "Runs" ? (
            <RunsPage
              repositories={repositories}
              runs={runs}
              operator={operator}
              setRuns={setRuns}
            />
          ) : page === "Repositories" ? (
            <RepositoryPage items={repositories} />
          ) : page === "Configuration" ? (
            <ConfigurationPage operator={operator}/>
          ) : page === "Guide" ? (
            <UserGuidePage />
          ) : (
            <AnalyticsPage analytics={analytics}/>
          )}
        </div>
      </div>
    </main>
  );
}

if (import.meta.env.MODE !== "test") {
  const root = document.getElementById("root");
  if (!root) throw new Error("ForgeLoop root element is missing");
  createRoot(root).render(window.location.pathname.startsWith("/app") ? <App /> : <LandingPage />);
}
export default App;
