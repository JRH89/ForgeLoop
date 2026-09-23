import {
  FormEvent,
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import { createRoot } from "react-dom/client";
import {
  approveFeatureRun,
  acknowledgeEscalation,
  cancelFeatureRun,
  loadOperator,
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
} from "./api";
import forgeLoopLogo from "./assets/logo.png";
import processGraphic from "./assets/forgeloop_process_infographic.png";
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
      <img
        className="process-graphic"
        src={processGraphic}
        alt="ForgeLoop delivery process"
      />
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
          <p>{run.specification}</p>
        </div>
        <span className={`status ${run.state.toLowerCase()}`}>
          {run.state.replaceAll("_", " ")}
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
            {!terminal.has(run.state) && canOperate && (
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
        {operations.artifacts.length > 0 && (
          <div className="item">
            <div>
              <b>Durable evidence artifacts</b>
              <small>
                {operations.artifacts.length} checksummed object{operations.artifacts.length === 1 ? "" : "s"} retained by policy
              </small>
            </div>
          </div>
        )}
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
        <section className="panel">
          <h2>GitHub delivery</h2>
          {operations.publication ? (
            <>
              <p>
                <b>{operations.publication.branch}</b>
              </p>
              <small>
                {operations.publication.headSha ?? "Branch pending"}
              </small>
              {operations.publication.pullRequestNumber && (
                <a
                  className="primary inline"
                  href={`https://github.com/${operations.publication.repository}/pull/${operations.publication.pullRequestNumber}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open pull request #{operations.publication.pullRequestNumber}
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
  const run = runs.find((item) => item.id === selected);
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
    if (!selected && runs[0]) setSelected(runs[0].id);
  }, [runs, selected]);
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
      <section className="page-title">
        <div>
          <p className="eyebrow">Delivery operations</p>
          <h1>Runs</h1>
          <p>From issue intake to verified, approved pull request.</p>
        </div>
        {operator.role !== "VIEWER" && (
          <button className="primary" onClick={() => setCreating(true)}>
            + New run
          </button>
        )}
      </section>
      <div className="workspace">
        <section className="panel run-list">
          <div className="filters">
            <b>Intake queue</b>
            <span>{runs.length} total</span>
          </div>
          {runs.length ? (
            runs.map((item) => (
              <button
                className={`run ${selected === item.id ? "selected" : ""}`}
                key={item.id}
                onClick={() => setSelected(item.id)}
              >
                <div>
                  <b>{item.title}</b>
                  <small>
                    {item.repository} · {item.sourceRef}
                  </small>
                </div>
                <span className={`status ${item.state.toLowerCase()}`}>
                  {item.state.replaceAll("_", " ")}
                </span>
              </button>
            ))
          ) : (
            <p className="empty">
              GitHub issues carrying the configured intake label will appear
              here automatically.
            </p>
          )}
        </section>
        <div>
          {run ? (
            <RunDetail
              run={run}
              operations={operations}
              operator={operator}
              onRefresh={refresh}
            />
          ) : (
            <section className="panel empty-state">
              <h2>No run selected</h2>
              <p>Connect a repository or submit a run to begin.</p>
            </section>
          )}
        </div>
      </div>
    </>
  );
}

function App() {
  const [page, setPage] = useState<"Runs" | "Repositories">("Runs");
  const [repositories, setRepositories] = useState<RepositoryConnection[]>([]);
  const [runs, setRuns] = useState<FeatureRun[]>([]);
  const [operator, setOperator] = useState<OperatorSession>();
  const [error, setError] = useState("");
  useEffect(() => {
    void Promise.all([loadRepositoryConnections(), loadRuns(), loadOperator()])
      .then(([connected, loaded, current]) => {
        setRepositories(connected);
        setRuns(loaded);
        setOperator(current);
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
            <img src={forgeLoopLogo} alt="ForgeLoop" />
          </a>
          <span>Autonomous software delivery control plane</span>
        </div>
        <div className="session">
          <i />
          {summary}
          {operator && (
            <span>
              {operator.role.toLowerCase()} · {operator.organizationId}
            </span>
          )}
        </div>
      </header>
      <div className="shell">
        <aside>
          <nav aria-label="ForgeLoop navigation">
            <button
              className={page === "Runs" ? "active" : ""}
              onClick={() => setPage("Runs")}
            >
              ◫ Runs
            </button>
            <button
              className={page === "Repositories" ? "active" : ""}
              onClick={() => setPage("Repositories")}
            >
              ⌘ Repositories
            </button>
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
          ) : (
            <RepositoryPage items={repositories} />
          )}
        </div>
      </div>
    </main>
  );
}

if (import.meta.env.MODE !== "test") {
  const root = document.getElementById("root");
  if (!root) throw new Error("ForgeLoop root element is missing");
  createRoot(root).render(<App />);
}
export default App;
