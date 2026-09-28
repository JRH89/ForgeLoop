import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  createIssueFromConversation,
  loadIssueConversation,
  loadIssueConversations,
  loadRunOperations,
  loadRuns,
  sendIssueConversationMessage,
  startIssueConversation,
  type FeatureRun,
  type IssueConversation,
  type RepositoryConnection,
  type RunOperations,
  type OperatorSession,
} from '../api';
import './issue-chat.css';

const terminal = new Set(['COMPLETE', 'CANCELLED', 'FAILED', 'REJECTED']);
const cost = (micros: number, known: boolean) => known ? `$${(micros / 1_000_000).toFixed(4)} estimated` : 'N/A';

/** Chat helps refine an issue only; source execution begins later through normal GitHub intake policy. */
export default function IssueChatPage({ repositories, operator, onRepositories }: {
  repositories: RepositoryConnection[];
  operator: OperatorSession;
  onRepositories: () => void;
}) {
  const enabledRepositories = useMemo(() => repositories.filter(item => item.enabled), [repositories]);
  const [repository, setRepository] = useState(enabledRepositories[0]?.repository ?? '');
  const initialConversation = new URLSearchParams(window.location.hash.slice(1)).get('chat');
  const [activeId, setActiveId] = useState(initialConversation && initialConversation !== '1' ? initialConversation : '');
  const [conversations, setConversations] = useState<IssueConversation[]>([]);
  const [conversation, setConversation] = useState<IssueConversation>();
  const [run, setRun] = useState<FeatureRun>();
  const [operations, setOperations] = useState<RunOperations>();
  const [draft, setDraft] = useState({ title: '', body: '', criteria: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');

  const refreshConversations = useCallback(async () => {
    if (!repository) return;
    setConversations(await loadIssueConversations(repository));
  }, [repository]);

  useEffect(() => {
    if (!repository && enabledRepositories.length) setRepository(enabledRepositories[0].repository);
  }, [repository, enabledRepositories]);

  useEffect(() => {
    let disposed = false;
    setConversations([]);
    setConversation(undefined);
    setRun(undefined);
    setOperations(undefined);
    if (repository) void loadIssueConversations(repository).then(items => { if (!disposed) setConversations(items); })
      .catch(reason => { if (!disposed) setError(messageOf(reason, 'Could not load issue chats.')); });
    return () => { disposed = true; };
  }, [repository]);

  useEffect(() => {
    let disposed = false;
    setConversation(undefined);
    setRun(undefined);
    setOperations(undefined);
    if (activeId) void loadIssueConversation(activeId).then(item => { if (!disposed) setConversation(item); })
      .catch(reason => { if (!disposed) setError(messageOf(reason, 'Could not load this issue chat.')); });
    return () => { disposed = true; };
  }, [activeId]);

  useEffect(() => {
    if (!conversation) return;
    setDraft({ title: conversation.draftTitle ?? '', body: conversation.draftBody ?? '', criteria: conversation.acceptanceCriteria.join('\n') });
  }, [conversation?.id, conversation?.status, conversation?.updatedAt]);

  useEffect(() => {
    if (!activeId || !conversation || !['PENDING', 'RUNNING'].includes(conversation.status)) return;
    let disposed = false;
    let timer = 0;
    async function poll() {
      try { const updated = await loadIssueConversation(activeId); if (!disposed) setConversation(updated); }
      catch (reason) { if (!disposed) setError(messageOf(reason, 'Live chat updates paused.')); }
      finally { if (!disposed) timer = window.setTimeout(() => void poll(), document.hidden ? 10000 : 2000); }
    }
    timer = window.setTimeout(() => void poll(), 1500);
    return () => { disposed = true; window.clearTimeout(timer); };
  }, [activeId, conversation?.status, conversation?.updatedAt]);

  useEffect(() => {
    if (!conversation?.issueNumber) return;
    const issueNumber = conversation.issueNumber;
    const repositoryName = conversation.repository;
    let disposed = false;
    let timer = 0;
    async function pollDelivery() {
      try {
        const runs = await loadRuns();
        const found = runs.find(item => item.repository === repositoryName && item.sourceRef === `issue-${issueNumber}`);
        if (!disposed && found) {
          setRun(found);
          setOperations(await loadRunOperations(found.id));
        }
      } catch (reason) { if (!disposed) setError(messageOf(reason, 'Delivery status refresh failed.')); }
      finally {
        if (!disposed && (!run || !terminal.has(run.state))) timer = window.setTimeout(() => void pollDelivery(), document.hidden ? 15000 : 5000);
      }
    }
    void pollDelivery();
    return () => { disposed = true; window.clearTimeout(timer); };
  }, [conversation?.repository, conversation?.issueNumber, run?.id, run?.state]);

  function select(id: string) {
    setError('');
    setActiveId(id);
    window.history.pushState(null, '', id ? `/app#chat=${encodeURIComponent(id)}` : '/app#chat');
  }

  async function start(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!repository || !message.trim()) return;
    await perform('start', async () => {
      const created = await startIssueConversation(repository, message.trim());
      setMessage('');
      setActiveId(created.id);
      window.history.pushState(null, '', `/app#chat=${encodeURIComponent(created.id)}`);
      await refreshConversations();
    });
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!conversation || !message.trim()) return;
    await perform('send', async () => {
      setConversation(await sendIssueConversationMessage(conversation.id, message.trim()));
      setMessage('');
      await refreshConversations();
    });
  }

  async function createIssue() {
    if (!conversation) return;
    const criteria = draft.criteria.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
    if (!window.confirm('Create this reviewed GitHub issue? It will be created without an intake label or assignee; repository policy controls when work starts.')) return;
    await perform('create', async () => {
      const updated = await createIssueFromConversation(conversation.id, draft.title.trim(), draft.body.trim(), criteria);
      setConversation(updated);
      await refreshConversations();
    });
  }

  async function perform(label: string, action: () => Promise<void>) {
    setBusy(label);
    setError('');
    try { await action(); }
    catch (reason) { setError(messageOf(reason, 'The issue chat action failed.')); }
    finally { setBusy(''); }
  }

  if (!enabledRepositories.length) return <section className="page-title"><div><p className="eyebrow">Runner-assisted intake</p><h1>Issue chat</h1><p>Connect a repository before drafting an issue.</p><button className="primary" onClick={onRepositories}>Open repositories</button></div></section>;

  const canCreate = operator.role === 'ADMIN' && conversation?.status === 'READY' && draft.title.trim() && draft.body.trim()
    && draft.criteria.split(/\r?\n/).some(item => item.trim());
  const waitingForIssue = conversation?.issueNumber && !run;
  const publication = operations?.publication;

  return <>
    <section className="page-title issue-chat-title">
      <div><p className="eyebrow">Runner-assisted intake</p><h1>Issue chat</h1><p>Describe the change, refine a reviewable specification, then choose whether to create the issue.</p></div>
      <label className="issue-chat-repository">Repository<select aria-label="Repository" value={repository} onChange={event => { setRepository(event.target.value); select(''); }}>
        {enabledRepositories.map(item => <option key={item.id} value={item.repository}>{item.repository}</option>)}
      </select></label>
    </section>
    <p className="issue-chat-cost-note">Messages and drafts are saved in this organization workspace; do not include secrets. Each reply may incur provider charges, shown under Usage &amp; costs.</p>
    {error && <p role="alert">{error}</p>}
    <div className="issue-chat-layout">
      <aside className="panel issue-chat-list" aria-label="Issue chat history">
        <div className="section-heading"><h2>Recent chats</h2><button className="secondary compact" onClick={() => select('')}>New chat</button></div>
        {conversations.length ? <ul>{conversations.map(item => <li key={item.id}>
          <button className={item.id === activeId ? 'active' : ''} onClick={() => select(item.id)}>
            <strong>{item.draftTitle || item.messages.find(entry => entry.role === 'USER')?.content || 'New issue chat'}</strong>
            <span>{item.status.replaceAll('_', ' ')} · {new Date(item.updatedAt).toLocaleDateString()}</span>
          </button>
        </li>)}</ul> : <p className="empty">Your issue chats will appear here.</p>}
      </aside>
      <section className="panel issue-chat-workspace">
        {!activeId ? <form onSubmit={start} className="issue-chat-start">
          <h2>What should change?</h2><p>Tell the runner what you want to accomplish. It will draft an issue, not modify your repository.</p>
          <label htmlFor="new-issue-prompt">Describe the issue</label>
          <textarea id="new-issue-prompt" value={message} onChange={event => setMessage(event.target.value)} maxLength={4000} required placeholder="For example: users should be able to filter runs by repository and status…" />
          <button className="primary" disabled={!!busy || !message.trim()}>{busy === 'start' ? 'Starting…' : 'Start issue chat'}</button>
        </form> : !conversation ? <p role="status">Loading issue chat…</p> : <>
          <div className="issue-chat-heading"><div><h2>{conversation.draftTitle || 'Issue specification'}</h2><p>{conversation.repository} · <span className={`status ${conversation.status.toLowerCase()}`}>{conversation.status.replaceAll('_', ' ')}</span></p></div>
            <small>{conversation.provider ? `${conversation.provider}/${conversation.model} · latest reply ${cost(conversation.estimatedCostMicros, conversation.costKnown)}` : 'Provider usage is recorded after each reply'}</small>
          </div>
          <ol className="issue-chat-messages" aria-label="Conversation">
            {conversation.messages.map((item, index) => <li key={`${item.createdAt}-${index}`} className={item.role.toLowerCase()}><b>{item.role === 'USER' ? 'You' : 'ForgeLoop assistant'}</b><p>{item.content}</p></li>)}
          </ol>
          {conversation.status === 'PENDING' || conversation.status === 'RUNNING' ? <p role="status" className="issue-chat-status">Your runner is drafting the next reply. This can take a little while.</p> : null}
          {conversation.status === 'FAILED' && <p role="alert">{conversation.failureSummary || 'The runner could not draft a response. Send another message to retry.'}</p>}
          {!conversation.issueNumber && conversation.status !== 'PENDING' && conversation.status !== 'RUNNING' && <form className="issue-chat-reply" onSubmit={send}>
            <label htmlFor="issue-chat-reply">Add detail or request a revision</label><textarea id="issue-chat-reply" value={message} onChange={event => setMessage(event.target.value)} maxLength={4000} placeholder="Clarify behavior, constraints, or acceptance criteria…" />
            <button className="secondary" disabled={!!busy || !message.trim()}>{busy === 'send' ? 'Sending…' : 'Send message'}</button>
          </form>}
          {conversation.draftTitle && <section className="issue-chat-draft" aria-labelledby="issue-chat-draft-heading">
            <div><p className="eyebrow">Human review</p><h3 id="issue-chat-draft-heading">Editable issue draft</h3></div>
            <label>Title<input value={draft.title} maxLength={200} onChange={event => setDraft({ ...draft, title: event.target.value })} disabled={!!conversation.issueNumber} /></label>
            <label>Issue description<textarea value={draft.body} maxLength={12000} onChange={event => setDraft({ ...draft, body: event.target.value })} disabled={!!conversation.issueNumber} /></label>
            <label>Acceptance criteria <small>One criterion per line</small><textarea value={draft.criteria} maxLength={4000} onChange={event => setDraft({ ...draft, criteria: event.target.value })} disabled={!!conversation.issueNumber} /></label>
            {conversation.issueNumber ? <p>Issue created: <a href={conversation.issueUrl} target="_blank" rel="noreferrer">#{conversation.issueNumber} on GitHub</a></p>
              : <><p className="issue-chat-intake-note">Creating an issue does not start work. Add the configured intake label and satisfy any required assignee rule in GitHub.</p>
                <button className="primary" onClick={() => void createIssue()} disabled={!canCreate || !!busy}>{busy === 'create' ? 'Creating issue…' : 'Review and create GitHub issue'}</button>
                {operator.role !== 'ADMIN' && <small>An organization administrator must approve issue creation.</small>}</>}
          </section>}
          {waitingForIssue && <section className="issue-chat-delivery"><h3>Waiting for repository intake</h3><p>Assign the issue and add the configured label in GitHub. ForgeLoop will start work only when this repository's intake policy is satisfied.</p><a href={conversation.issueUrl} target="_blank" rel="noreferrer">Open issue #{conversation.issueNumber}</a></section>}
          {run && <DeliveryTracker run={run} operations={operations} publication={publication} />}
        </>}
      </section>
    </div>
  </>;
}

function DeliveryTracker({ run, operations, publication }: { run: FeatureRun; operations?: RunOperations; publication?: RunOperations['publication'] }) {
  return <section className="issue-chat-delivery" aria-label="Linked delivery status">
    <div className="section-heading"><div><p className="eyebrow">Linked delivery</p><h3>{run.title}</h3></div><span className={`status ${run.state.toLowerCase()}`}>{run.state.replaceAll('_', ' ')}</span></div>
    <p>{run.repository} · {run.sourceRef}</p>
    <div className="issue-chat-gates"><b>Verification gates</b>{run.gates.map(gate => <span key={gate.id}>{gate.name}: {gate.state.replaceAll('_', ' ')}</span>)}</div>
    {operations?.evidence.length ? <details><summary>Verification evidence ({operations.evidence.length})</summary><ul>{operations.evidence.map(item => <li key={item.id}><b>{item.gate}</b> · {item.exitCode === 0 && !item.timedOut ? 'passed' : 'failed'} · <code>{item.digest.slice(0, 12)}</code></li>)}</ul></details> : <p>Detailed verification evidence will appear here as the runner reports it.</p>}
    {publication?.pullRequestNumber && <a className="primary compact" href={`https://github.com/${publication.repository}/pull/${publication.pullRequestNumber}`} target="_blank" rel="noreferrer">View pull request #{publication.pullRequestNumber}</a>}
    {run.state === 'READY_FOR_REVIEW' && !run.approved && <p>Verification is complete. Review evidence and approve delivery in Runs. The configured merge policy applies after checks pass.</p>}
    <a href={`/app#run=${encodeURIComponent(run.id)}`}>Open this run in Runs</a>
    {publication?.mergedAt && <p>PR merged {new Date(publication.mergedAt).toLocaleString()} · commit {publication.mergeSha?.slice(0, 12)}</p>}
  </section>;
}

function messageOf(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback; }
