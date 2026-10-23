import { useState } from 'react';
import { approveFeatureRun, acknowledgeEscalation, cancelFeatureRun, retryFeatureTask, resolveEscalation, type FeatureRun, type OperatorSession, type RunOperations } from '../api';
import MarkdownContent from '../MarkdownContent';
import { terminal, retryable, money, costSummary, stamp } from './presentation';

export default function RunDetail({
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
  const canOperate = operator.role !== "VIEWER" && !run.archived;
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
          <b>{costSummary(run)}</b>
          <span>of ${run.budgetUsd.toFixed(2)}</span>
        </article>
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
            A pull request is published after verification and any required
            approval.
          </p>
        )}
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
              run.requiresHumanApproval !== false &&
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
            {run.state === "READY_FOR_REVIEW" && run.requiresHumanApproval === false && (
              <p role="status">Verification passed. Publishing automatically; no human approval is required.</p>
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
          {costSummary(run)} ·{" "}
          {money(Math.max(0, budget - run.spentCostMicros))} remaining against priced usage only · policy
          revision {run.policyRevision}
        </small>
        {error && <p role="alert">{error}</p>}
      </section>
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Task graph & attempts">
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
                    {attempt.costKnown?money(attempt.estimatedCostMicros):'Unpriced'}
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
        <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Verification gates">
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
        <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Acceptance criteria">
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
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Runner event stream">
        <div className="section-heading"><div><h2>Runner event stream</h2><p>Redacted lease progress refreshes while this run is active.</p></div></div>
        {operations.events.length ? operations.events.map(event=><div className="timeline" key={event.id}><i/><div><b>{event.eventType.replaceAll("_"," ")}</b><small>{event.message} · {stamp(event.occurredAt)}</small></div></div>):<p className="empty">No runner events have been received.</p>}
      </section>
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Human escalation queue">
        <div className="section-heading"><div><h2>Human escalation queue</h2><p>Automation stops safely when a configured boundary is reached.</p></div></div>
        {operations.escalations.length ? operations.escalations.map(item=><div className="item" key={item.id}><div><b>{item.reason.replaceAll("_"," ")}</b><small>{item.summary} · {item.severity} · {stamp(item.createdAt)}</small></div><div className="actions"><span className={`status ${item.status.toLowerCase()}`}>{item.status}</span>{canOperate&&item.status==="OPEN"&&<button className="secondary" disabled={!!busy} onClick={()=>void action(`ack-${item.id}`,()=>acknowledgeEscalation(item.id))}>Acknowledge</button>}{canOperate&&item.status!=="RESOLVED"&&<button className="secondary" disabled={!!busy} onClick={()=>void action(`resolve-${item.id}`,()=>resolveEscalation(item.id))}>Resolve</button>}</div></div>):<p className="empty">No human intervention is required.</p>}
      </section>
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Independent review">
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
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Evidence browser & redacted logs">
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
      <section className="panel run-scroll-panel" tabIndex={0} role="region" aria-label="Audit timeline">
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
    </div>
  );
}
