import { FormEvent, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { loadRepositoryConnections, loadRuns, submitFeature, type FeatureRun, type RepositoryConnection } from './api';
import forgeLoopLogo from './assets/logo.png';
import processGraphic from './assets/forgeloop_process_infographic.png';
import './styles.css';

function RepositoryPage({ items }: { items: RepositoryConnection[] }) {
  return <>
    <section className="hero"><p className="eyebrow">Repository authorization</p><h1>Connect policy, not source code.</h1><p>Install the ForgeLoop GitHub App and choose the repositories it may access. ForgeLoop receives the installation identity from GitHub; users never type an installation ID.</p></section>
    <img className="process-graphic" src={processGraphic} alt="ForgeLoop delivery process" />
    <section className="submission"><h2>Connect GitHub</h2><p>The App installation opens in GitHub, where an organization owner selects all or specific repositories.</p><a className="primary" href="/api/github/app/install">Install ForgeLoop GitHub App</a><p>After GitHub returns the selected repositories, ForgeLoop applies the repository policy and enables issue intake.</p></section>
    <section className="runs"><h2>Connected repositories</h2>{items.map(item => <div className="item" key={item.id}><div><b>{item.repository}</b><small>{item.defaultBranch} · {item.issueLabel} · {item.requiredGates.join(', ')}</small></div><span>Policy v{item.policyRevision}</span></div>)}</section>
  </>;
}

function RunsPage({ items, runs, onAdded }: { items: RepositoryConnection[]; runs: FeatureRun[]; onAdded: (run: FeatureRun) => void }) {
  const [repository, setRepository] = useState(''); const [sourceRef, setSourceRef] = useState(''); const [title, setTitle] = useState(''); const [specification, setSpecification] = useState(''); const [error, setError] = useState('');
  async function submit(event: FormEvent) { event.preventDefault(); try { onAdded(await submitFeature({ repository, sourceRef, title, specification, budgetUsd: 25 })); } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to create run'); } }
  return <><section className="hero"><p className="eyebrow">Delivery operations</p><h1>Verified changes, not agent claims.</h1></section><form className="submission" onSubmit={event => void submit(event)}><h2>Start a delivery run</h2><label>Authorized repository<select aria-label="Authorized repository" required value={repository} onChange={event => setRepository(event.target.value)}><option value="">Select repository</option>{items.filter(item => item.enabled).map(item => <option key={item.id} value={item.repository}>{item.repository}</option>)}</select></label><label>Issue or source reference<input aria-label="Issue or source reference" required value={sourceRef} onChange={event => setSourceRef(event.target.value)} /></label><label>Title<input aria-label="Title" required value={title} onChange={event => setTitle(event.target.value)} /></label><label>Specification<textarea aria-label="Specification" required value={specification} onChange={event => setSpecification(event.target.value)} /></label><button className="primary" disabled={!items.some(item => item.enabled)}>Create delivery run</button>{error && <p role="alert">{error}</p>}</form><section className="runs"><h2>Delivery runs</h2>{runs.map(run => <div className="item" key={run.id}><div><b>{run.title}</b><small>{run.repository} · {run.sourceRef}</small></div><span>{run.state}</span></div>)}</section></>;
}

function App() {
  const [page, setPage] = useState<'Runs' | 'Repositories'>('Runs'); const [repositories, setRepositories] = useState<RepositoryConnection[]>([]); const [runs, setRuns] = useState<FeatureRun[]>([]);
  useEffect(() => { void Promise.all([loadRepositoryConnections(), loadRuns()]).then(([connected, loaded]) => { setRepositories(connected); setRuns(loaded); }); }, []);
  return <main><header><div><a className="brand" href="/"><img src={forgeLoopLogo} alt="ForgeLoop" /></a><span>Autonomous software delivery control plane</span></div></header><div className="shell"><aside><nav aria-label="ForgeLoop navigation"><button className={page === 'Runs' ? 'active' : ''} onClick={() => setPage('Runs')}>Runs</button><button className={page === 'Repositories' ? 'active' : ''} onClick={() => setPage('Repositories')}>Repositories</button></nav></aside><div className="content">{page === 'Runs' ? <RunsPage items={repositories} runs={runs} onAdded={run => setRuns(current => [run, ...current])} /> : <RepositoryPage items={repositories} />}</div></div></main>;
}

if (import.meta.env.MODE !== 'test') { const root = document.getElementById('root'); if (!root) throw new Error('ForgeLoop root element is missing'); createRoot(root).render(<App />); }
export default App;
