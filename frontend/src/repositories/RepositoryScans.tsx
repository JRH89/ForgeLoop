import { useCallback, useEffect, useState } from 'react';
import { createRepositoryScanIssue, loadRepositoryScans, requestRepositoryScan, type RepositoryScan, type RepositoryScanFinding } from '../api';
import './repository-scans.css';

const scanCost = (scan:RepositoryScan) => scan.costKnown
  ? `~$${(scan.estimatedCostMicros/1_000_000).toFixed(4)}`
  : 'N/A';

/** Manual runner-backed analysis. Findings remain proposals until an administrator publishes them. */
export default function RepositoryScans({repository,isAdmin}:{repository:string;isAdmin:boolean}) {
  const [scans,setScans]=useState<RepositoryScan[]>([]);
  const [confirming,setConfirming]=useState(false);
  const [busy,setBusy]=useState(false);
  const [creating,setCreating]=useState('');
  const [error,setError]=useState('');
  const refresh=useCallback(()=>loadRepositoryScans(repository).then(setScans).catch(reason=>setError(reason instanceof Error?reason.message:'Unable to load repository scans')),[repository]);
  useEffect(()=>{void refresh();},[refresh]);
  const active=scans.some(scan=>scan.status==='PENDING'||scan.status==='RUNNING');
  useEffect(()=>{
    if(!active)return;
    const timer=window.setInterval(()=>{void refresh();},3000);
    return ()=>window.clearInterval(timer);
  },[active,refresh]);

  const start=()=>{
    setBusy(true);setError('');
    void requestRepositoryScan(repository).then(scan=>{setScans(current=>[scan,...current.filter(item=>item.id!==scan.id)].slice(0,10));setConfirming(false);})
      .catch(reason=>setError(reason instanceof Error?reason.message:'Unable to start repository scan')).finally(()=>setBusy(false));
  };
  const createIssue=(scan:RepositoryScan,finding:RepositoryScanFinding)=>{
    setCreating(finding.id);setError('');
    void createRepositoryScanIssue(scan.id,finding.id).then(updated=>setScans(current=>current.map(item=>item.id===scan.id?{...item,findings:item.findings.map(value=>value.id===updated.id?updated:value)}:item)))
      .catch(reason=>setError(reason instanceof Error?reason.message:'Unable to create GitHub issue')).finally(()=>setCreating(''));
  };

  return <section className="repository-scans" aria-label={`Repository analysis for ${repository}`}>
    <div className="repository-scans-heading">
      <div><h3>Repository issue scan</h3><p>Find evidence-backed work proposals from the current default branch.</p></div>
      {isAdmin&&!confirming&&<button type="button" className="secondary" onClick={()=>setConfirming(true)} disabled={active}>Analyze repository</button>}
    </div>
    {confirming&&<div className="repository-scan-confirm">
      <p>This read-only scan sends a bounded repository context, with common credentials and private keys redacted, to the provider configured on your runner. Provider charges may apply. ForgeLoop stores findings and usage metadata, not the source context; it will not edit files or create GitHub issues automatically.</p>
      <div><button type="button" className="primary" onClick={start} disabled={busy}>{busy?'Queueing…':'Start manual scan'}</button><button type="button" className="secondary" onClick={()=>setConfirming(false)} disabled={busy}>Cancel</button></div>
    </div>}
    {error&&<p className="repository-scan-error" role="alert">{error}</p>}
    {scans.length===0?<p className="repository-scan-empty">No repository scans yet. An administrator can start one manually.</p>:
      <div className="repository-scan-list">{scans.map(scan=><article className="repository-scan-card" key={scan.id}>
        <div className="repository-scan-meta"><span className={`status ${scan.status==='COMPLETE'?'complete':scan.status==='FAILED'?'failed':''}`}>{scan.status}</span><span>{new Date(scan.createdAt).toLocaleString()}</span>
          {scan.model&&<span>{scan.provider} · {scan.model} · {scanCost(scan)}</span>}{scan.commitSha&&<code title={scan.commitSha}>{scan.commitSha.slice(0,12)}</code>}</div>
        {scan.failureSummary&&<p className="repository-scan-error">{scan.failureSummary}</p>}
        {scan.status==='PENDING'&&<p className="repository-scan-muted">Waiting for an online runner. It starts only because you requested this scan.</p>}
        {scan.status==='RUNNING'&&<p className="repository-scan-muted">Read-only analysis is running on your runner…</p>}
        {scan.status==='COMPLETE'&&scan.findings.length===0&&<p className="repository-scan-muted">No actionable findings met the evidence threshold for this snapshot.</p>}
        {scan.findings.map(finding=><div className="repository-scan-finding" key={finding.id}>
          <div className="repository-scan-finding-heading"><span className={`severity severity-${finding.severity.toLowerCase()}`}>{finding.severity}</span><h4>{finding.title}</h4></div>
          <p>{finding.description}</p><p><b>Impact:</b> {finding.impact}</p><p><b>Evidence:</b> {finding.evidence}</p>
          <p><b>Files:</b> {finding.affectedFiles.map(file=><code key={file}>{file}</code>)}</p>
          <ul>{finding.acceptanceCriteria.map(item=><li key={item}>{item}</li>)}</ul>
          {finding.issueUrl?<a href={finding.issueUrl} target="_blank" rel="noreferrer">GitHub issue #{finding.issueNumber}</a>:
            isAdmin&&<button type="button" className="secondary" disabled={creating===finding.id} onClick={()=>createIssue(scan,finding)}>{creating===finding.id?'Creating…':'Create GitHub issue'}</button>}
        </div>)}
      </article>)}</div>}
    {!isAdmin&&scans.length>0&&<p className="repository-scan-muted">Only organization administrators can start scans or create issues.</p>}
  </section>;
}
