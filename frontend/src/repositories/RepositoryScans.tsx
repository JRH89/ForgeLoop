import { useCallback, useEffect, useState } from 'react';
import {
  approveIssueProposal,
  loadRepositoryScans,
  rejectIssueProposal,
  requestIssueProposal,
  requestRepositoryScan,
  type RepositoryIssueProposal,
  type RepositoryScan,
  type RepositoryScanFinding,
} from '../api';
import './repository-scans.css';

type IssueDraft = { title: string; body: string; criteria: string };

const scanCost = (scan: RepositoryScan) => scan.costKnown
  ? `~$${(scan.estimatedCostMicros / 1_000_000).toFixed(4)}`
  : 'N/A';

const proposalCost = (proposal: RepositoryIssueProposal) => proposal.costKnown
  ? `~$${(proposal.estimatedCostMicros / 1_000_000).toFixed(4)}`
  : 'N/A';

/** Manual runner-backed analysis. Draft generation is separately metered and publication always needs review. */
export default function RepositoryScans({ repository, isAdmin }: { repository: string; isAdmin: boolean }) {
  const [scans, setScans] = useState<RepositoryScan[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [workingOn, setWorkingOn] = useState('');
  const [drafts, setDrafts] = useState<Record<string, IssueDraft>>({});
  const [error, setError] = useState('');
  const refresh = useCallback(() => loadRepositoryScans(repository).then(setScans)
    .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to load repository scans')), [repository]);

  useEffect(() => { void refresh(); }, [refresh]);
  const active = scans.some(scan => scan.status === 'PENDING' || scan.status === 'RUNNING'
    || scan.findings.some(finding => finding.proposal?.status === 'PENDING' || finding.proposal?.status === 'RUNNING'));
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => { void refresh(); }, 3000);
    return () => window.clearInterval(timer);
  }, [active, refresh]);

  const updateProposal = (scanId: string, findingId: string, proposal: RepositoryIssueProposal) => {
    setScans(current => current.map(scan => scan.id === scanId ? {
      ...scan,
      findings: scan.findings.map(finding => finding.id === findingId ? {
        ...finding,
        proposal,
        issueNumber: proposal.issueNumber ?? finding.issueNumber,
        issueUrl: proposal.issueUrl ?? finding.issueUrl,
      } : finding),
    } : scan));
  };

  const start = () => {
    setBusy(true);
    setError('');
    void requestRepositoryScan(repository)
      .then(scan => { setScans(current => [scan, ...current.filter(item => item.id !== scan.id)].slice(0, 10)); setConfirming(false); })
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to start repository scan'))
      .finally(() => setBusy(false));
  };

  const generate = (scan: RepositoryScan, finding: RepositoryScanFinding) => {
    setWorkingOn(finding.id);
    setError('');
    void requestIssueProposal(scan.id, finding.id)
      .then(proposal => updateProposal(scan.id, finding.id, proposal))
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to queue issue proposal'))
      .finally(() => setWorkingOn(''));
  };

  const approve = (scan: RepositoryScan, finding: RepositoryScanFinding, proposal: RepositoryIssueProposal) => {
    const draft = drafts[proposal.id] ?? {
      title: proposal.proposedTitle ?? '',
      body: proposal.proposedBody ?? '',
      criteria: (proposal.acceptanceCriteria ?? []).join('\n'),
    };
    const criteria = draft.criteria.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
    setWorkingOn(finding.id);
    setError('');
    void approveIssueProposal(proposal.id, draft.title, draft.body, criteria)
      .then(updated => updateProposal(scan.id, finding.id, updated))
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to publish the reviewed issue'))
      .finally(() => setWorkingOn(''));
  };

  const reject = (scan: RepositoryScan, finding: RepositoryScanFinding, proposal: RepositoryIssueProposal) => {
    setWorkingOn(finding.id);
    setError('');
    void rejectIssueProposal(proposal.id)
      .then(updated => updateProposal(scan.id, finding.id, updated))
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to reject issue proposal'))
      .finally(() => setWorkingOn(''));
  };

  const editDraft = (proposal: RepositoryIssueProposal, change: Partial<IssueDraft>) => {
    const current = drafts[proposal.id] ?? {
      title: proposal.proposedTitle ?? '',
      body: proposal.proposedBody ?? '',
      criteria: (proposal.acceptanceCriteria ?? []).join('\n'),
    };
    setDrafts(values => ({ ...values, [proposal.id]: { ...current, ...change } }));
  };

  return <section className="repository-scans" aria-label={`Repository analysis for ${repository}`}>
    <div className="repository-scans-heading">
      <div><h3>Repository issue scan</h3><p>Find evidence-backed work proposals from the current default branch.</p></div>
      {isAdmin && !confirming && <button type="button" className="secondary" onClick={() => setConfirming(true)} disabled={active}>Analyze repository</button>}
    </div>
    {confirming && <div className="repository-scan-confirm">
      <p>This read-only scan sends a bounded repository context, with common credentials and private keys redacted, to the provider configured on your runner. Provider charges may apply. ForgeLoop stores findings and usage metadata, not the source context; it will not edit files or create GitHub issues automatically.</p>
      <div><button type="button" className="primary" onClick={start} disabled={busy}>{busy ? 'Queueing…' : 'Start manual scan'}</button><button type="button" className="secondary" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button></div>
    </div>}
    {error && <p className="repository-scan-error" role="alert">{error}</p>}
    {scans.length === 0 ? <p className="repository-scan-empty">No repository scans yet. An administrator can start one manually.</p> :
      <div className="repository-scan-list">{scans.map(scan => <article className="repository-scan-card" key={scan.id}>
        <div className="repository-scan-meta"><span className={`status ${scan.status === 'COMPLETE' ? 'complete' : scan.status === 'FAILED' ? 'failed' : ''}`}>{scan.status}</span><span>{new Date(scan.createdAt).toLocaleString()}</span>
          {scan.model && <span>{scan.provider} · {scan.model} · {scanCost(scan)}</span>}{scan.commitSha && <code title={scan.commitSha}>{scan.commitSha.slice(0, 12)}</code>}</div>
        {scan.failureSummary && <p className="repository-scan-error">{scan.failureSummary}</p>}
        {scan.status === 'PENDING' && <p className="repository-scan-muted">Waiting for an online runner. It starts only because you requested this scan.</p>}
        {scan.status === 'RUNNING' && <p className="repository-scan-muted">Read-only analysis is running on your runner…</p>}
        {scan.status === 'COMPLETE' && scan.findings.length === 0 && <p className="repository-scan-muted">No actionable findings met the evidence threshold for this snapshot.</p>}
        {scan.findings.map(finding => {
          const proposal = finding.proposal;
          const draft = proposal ? drafts[proposal.id] ?? {
            title: proposal.proposedTitle ?? '', body: proposal.proposedBody ?? '', criteria: (proposal.acceptanceCriteria ?? []).join('\n'),
          } : undefined;
          return <div className="repository-scan-finding" key={finding.id}>
            <div className="repository-scan-finding-heading"><span className={`severity severity-${finding.severity.toLowerCase()}`}>{finding.severity}</span><h4>{finding.title}</h4></div>
            <p>{finding.description}</p><p><b>Impact:</b> {finding.impact}</p><p><b>Evidence:</b> {finding.evidence}</p>
            <p><b>Files:</b> {finding.affectedFiles.map(file => <code key={file}>{file}</code>)}</p>
            <ul>{finding.acceptanceCriteria.map(item => <li key={item}>{item}</li>)}</ul>
            {finding.issueUrl && proposal?.status !== 'APPROVED' && <a href={finding.issueUrl} target="_blank" rel="noreferrer">GitHub issue #{finding.issueNumber}</a>}
            {!finding.issueUrl && isAdmin && (!proposal || proposal.status === 'FAILED' || proposal.status === 'REJECTED') &&
              <div className="proposal-action"><p>Drafting a full issue uses an additional provider call. Its price is reported separately after the runner responds.</p>
                <button type="button" className="secondary" disabled={workingOn === finding.id} onClick={() => generate(scan, finding)}>
                  {workingOn === finding.id ? 'Queueing…' : proposal ? 'Generate a new issue draft' : 'Generate issue draft · extra model call'}
                </button>
              </div>}
            {proposal && <section className="issue-proposal" aria-label="Generated issue proposal">
              <div className="proposal-meta"><strong>Issue proposal · {proposal.status}</strong>
                {proposal.model && <span>{proposal.provider} / {proposal.model} · {proposalCost(proposal)} · {(proposal.inputTokens + proposal.outputTokens).toLocaleString()} tokens</span>}
                {!proposal.model && proposal.status === 'FAILED' && <span>Cost N/A · provider returned no usage details</span>}
              </div>
              {proposal.status === 'PENDING' && <p className="repository-scan-muted">Queued on your runner. No issue will be created unless you review and approve the draft.</p>}
              {proposal.status === 'RUNNING' && <p className="repository-scan-muted">The runner is drafting an issue from the saved finding…</p>}
              {proposal.status === 'FAILED' && <p className="repository-scan-error">{proposal.failureSummary ?? 'Draft generation failed. You can retry with another explicit provider call.'}</p>}
              {proposal.status === 'REJECTED' && <p className="repository-scan-muted">You rejected this draft. You can generate a new one if needed.</p>}
              {proposal.status === 'READY' && draft && <div className="issue-proposal-editor">
                <label>Issue title<input maxLength={200} value={draft.title} onChange={event => editDraft(proposal, { title: event.target.value })} /></label>
                <label>Issue description<textarea maxLength={12000} rows={7} value={draft.body} onChange={event => editDraft(proposal, { body: event.target.value })} /></label>
                <label>Acceptance criteria <span>One criterion per line</span><textarea maxLength={4000} rows={5} value={draft.criteria} onChange={event => editDraft(proposal, { criteria: event.target.value })} /></label>
                <div className="proposal-actions"><button type="button" className="primary" disabled={workingOn === finding.id} onClick={() => approve(scan, finding, proposal)}>{workingOn === finding.id ? 'Publishing…' : 'Approve & create GitHub issue'}</button>
                  <button type="button" className="secondary" disabled={workingOn === finding.id} onClick={() => reject(scan, finding, proposal)}>Reject draft</button></div>
              </div>}
              {proposal.status === 'APPROVED' && proposal.issueUrl && <p><a href={proposal.issueUrl} target="_blank" rel="noreferrer">GitHub issue #{proposal.issueNumber}</a> created after approval.</p>}
            </section>}
          </div>;
        })}
      </article>)}</div>}
    {!isAdmin && scans.length > 0 && <p className="repository-scan-muted">Only organization administrators can start scans or generate, approve, and reject issue proposals.</p>}
  </section>;
}
