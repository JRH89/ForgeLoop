import { useEffect, useRef, useState, type FormEvent } from 'react';
import { checkIssueIntake, loadPlatformConfiguration, loadRepositoryConnections, loadRunners, loadRuns,
  type FeatureRun, type IssueIntakeCheck, type OperatorSession, type PlatformConfiguration, type RepositoryConnection, type Runner } from '../api';
import { configurationWarnings, newIssueUrl, recentRunners, runExplanation } from './readiness';
import { parseIssueReference } from './issueReference';
import './onboarding.css';

type Snapshot = { repositories: RepositoryConnection[]; runners: Runner[]; runs: FeatureRun[]; config: PlatformConfiguration; at: number };
type Props = { operator: OperatorSession; navigate: (page: 'Repositories' | 'Configuration' | 'Guide' | 'Runs') => void };

/** Poll only control-plane metadata. GitHub issue reads are explicit and model credentials never enter this page. */
export default function OnboardingPage({ operator, navigate }: Props) {
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState('');
  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const [repositories, runners, runs, config] = await Promise.all([
          loadRepositoryConnections(), loadRunners(operator.organizationId), loadRuns(), loadPlatformConfiguration(),
        ]);
        if (!disposed) { setSnapshot({ repositories, runners, runs, config, at: Date.now() }); setError(''); }
      } catch {
        if (!disposed) setError('Readiness could not be refreshed. Previous results may be stale. Check your connection and retry.');
      } finally {
        // Schedule after completion so a slow control plane never produces overlapping polls.
        if (!disposed) timer = setTimeout(() => void poll(), 10_000);
      }
    }
    void poll();
    return () => { disposed = true; clearTimeout(timer); };
  }, [operator.organizationId, refresh]);

  const repository = snapshot?.repositories.find(item => item.repository === selected) ?? snapshot?.repositories[0];
  const warnings = snapshot && repository ? configurationWarnings(repository, snapshot.config) : [];
  const recent = snapshot ? recentRunners(snapshot.runners, snapshot.at) : [];
  const issueUrl = repository && newIssueUrl(repository.repository, repository.issueLabel, repository.requiredAssignee, repository.requireAssignee);
  const admin = operator.role === 'ADMIN';
  return <section className="onboarding">
    <h1>Getting started</h1>
    <p>Connect a repository to your own runner, then follow one issue through verified delivery.</p>
    <p className="setup-notice">These checks do not call a model or start work. Provider keys stay on your runner. Starting a runner with eligible queued work can spend API credits.</p>
    {error && <p role="alert">{error}</p>}
    <button onClick={() => setRefresh(value => value + 1)}>Refresh readiness</button>
    {!snapshot ? <p role="status">{error ? 'Readiness unavailable.' : 'Checking your workspace…'}</p> : <>
      <p role="status">{error ? 'Last successful check' : 'Updated'} {new Date(snapshot.at).toLocaleTimeString()} · Refreshes every 10 seconds</p>
      <ol className="setup-steps">
        <li><h2>Connect GitHub</h2>
          <p>{snapshot.repositories.some(item => item.enabled) ? 'A repository connection is saved for this organization.' : 'No enabled repository connection was found.'} This is not a live check of every GitHub permission.</p>
          {admin ? <a href="/api/github/app/install">Install or manage ForgeLoop GitHub App</a> : <p>Ask your organization administrator to install the GitHub App and select repositories.</p>}
        </li>
        <li><h2>Select your repository</h2>
          {repository ? <><label htmlFor="setup-repository">Repository</label><select id="setup-repository" value={repository.repository} onChange={event => setSelected(event.target.value)}>
            {snapshot.repositories.map(item => <option key={item.id} value={item.repository}>{item.repository}{!item.enabled ? ' (disabled)' : ''}</option>)}
          </select><p>Base branch: {repository.defaultBranch} · Harness: {repository.harnessProfile}</p></> : <p>Install the GitHub App on at least one repository, then refresh this checklist.</p>}
          <button onClick={() => navigate('Repositories')}>Open repositories</button>
        </li>
        <li><h2>Connect and check your runner</h2>
          <p>{recent.length ? `${recent.length} runner(s) have a recent heartbeat.` : snapshot.runners.some(item => item.enabled) ? 'Heartbeat stale: check the desktop connection and pause state. Active work can delay heartbeats.' : 'No enabled runner is enrolled.'}</p>
          <p>Install the desktop app, approve its matching fingerprint, save your provider settings, and use <strong>Check saved key locally</strong> and <strong>Check saved connection</strong>. The website cannot verify your local key, provider balance, Git, or Docker. Leave the runner paused until you are ready to spend.</p>
          <a href="/app/runner-downloads">Download desktop runner</a> <button onClick={() => navigate('Configuration')}>Manage runners</button>
        </li>
        <li><h2>Review intake and safety settings</h2>
          {repository ? <><p>Required label: <code>{repository.issueLabel}</code> · {repository.requireAssignee ? <>Required assignee: {repository.requiredAssignee ? <code>{repository.requiredAssignee}</code> : 'any assigned user'}</> : 'No assignee required; the label controls intake.'}</p>
            <p>Repository budget: ${repository.maxBudgetUsd} · Organization limit: ${snapshot.config.policy.maxRunBudgetUsd} · Human approval: {snapshot.config.policy.requireHumanApproval ? 'required' : 'not required'} · Auto-merge: {snapshot.config.policy.autoMergeEnabled ? 'enabled' : 'disabled'}</p>
            {warnings.length ? <ul>{warnings.map(warning => <li key={warning}>{warning}</li>)}</ul> : <p>Saved harness and intake configuration checks pass. This does not verify provider funding or the repository build environment.</p>}
            <p>Verification gates: {repository.requiredGates.join(', ') || 'None configured; review your verification policy before using real work.'}</p></> : <p>Select a connected repository first.</p>}
          <button onClick={() => navigate('Repositories')}>Review intake settings</button> <button onClick={() => navigate('Configuration')}>Review harness &amp; policy</button>
        </li>
        <li><h2>Create and follow your first issue</h2>
          <p>Start with one small change and explicit acceptance criteria. The GitHub draft includes a safe title and checklist, but does not apply the intake label or assignee.</p>
          {repository && <p>An issue enters intake when it has the required label <code>{repository.issueLabel}</code>{repository.requireAssignee ? <> and {repository.requiredAssignee ? <>assignee <code>{repository.requiredAssignee}</code></> : 'any GitHub assignee'}</> : null}. Add whichever is missing only when you intend to activate this work. A running worker can make paid provider calls as soon as an issue becomes eligible.</p>}
          {issueUrl && <a href={issueUrl} target="_blank" rel="noreferrer">Draft a safe issue on GitHub</a>}
          {repository?.enabled && <IssueCheck key={repository.repository} repository={repository.repository} />}
          <p>Intake handles opened, labeled, assigned, and reopened issue events. Editing the description alone does not trigger intake. If an eligible issue has no run, check GitHub App webhook deliveries; do not repeatedly create issues or replay work.</p>
          <button onClick={() => navigate('Runs')}>Open runs and live evidence</button>
        </li>
      </ol>
      <h2>What is waiting?</h2>
      {snapshot.runs.filter(run => !run.archived && run.repository === repository?.repository).length ? <ul className="setup-runs">
        {snapshot.runs.filter(run => !run.archived && run.repository === repository?.repository).map(run => <li key={run.id}><strong>{run.sourceRef}: {run.title}</strong><p>{runExplanation(run, snapshot.runners, snapshot.at)}</p></li>)}
      </ul> : <p>No unarchived runs for this repository. Use the issue check above to diagnose intake before starting work.</p>}
    </>}
    <button onClick={() => navigate('Guide')}>Read the complete user guide</button>
  </section>;
}

function IssueCheck({ repository }: { repository: string }) {
  const [reference, setReference] = useState('');
  const [result, setResult] = useState<IssueIntakeCheck>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const generation = useRef(0);
  useEffect(() => () => { generation.current++; }, []);
  async function check(event: FormEvent) {
    event.preventDefault();
    const parsed = parseIssueReference(reference, repository);
    if ('error' in parsed) { setResult(undefined); setError(parsed.error); return; }
    const current = ++generation.current;
    setBusy(true); setResult(undefined); setError('');
    try {
      const response = await checkIssueIntake(repository, parsed.issueNumber);
      if (current === generation.current) setResult(response);
    } catch {
      if (current === generation.current) setError('Unable to check this issue. Verify its number, your session, and GitHub App access, then retry.');
    } finally { if (current === generation.current) setBusy(false); }
  }
  return <form className="setup-issue" onSubmit={event => void check(event)}>
    <label htmlFor="setup-issue-reference">GitHub issue number or URL</label>
    <input id="setup-issue-reference" type="text" inputMode="url" autoComplete="off" placeholder="42 or https://github.com/owner/repo/issues/42" required value={reference} disabled={busy} onChange={event => { setReference(event.target.value); setResult(undefined); setError(''); }} />
    <button disabled={busy} type="submit">{busy ? 'Checking issue…' : 'Check intake — read only'}</button>
    <p>Reads an issue only from the selected repository. It does not modify the issue, replay a webhook, submit a run, or call a model.</p>
    {error && <p role="alert">{error}</p>}
    {result && <div role="status"><p>{result.eligible ? 'Intake rules pass. This is not confirmation that GitHub delivered a webhook or that a run started.' : 'This issue is not eligible for intake:'}</p>
      <ul>{result.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul><p>Checked {new Date(result.checkedAt).toLocaleTimeString()}</p></div>}
  </form>;
}
