import { useEffect, useState } from 'react';
import { issueRunnerToken, loadRunners, type OperatorSession, type Runner } from './api';

/** Enrollment secrets stay in component memory, expire locally, and are never stored in browser storage. */
export default function RunnerSetup({operator}:{operator:OperatorSession}) {
  const [runners,setRunners]=useState<Runner[]>([]);
  const [token,setToken]=useState('');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [expires,setExpires]=useState(0);
  useEffect(()=>{
    let stopped=false;
    const update=()=>loadRunners(operator.organizationId).then(items=>{if(!stopped){setRunners(items??[]);setError('');}}).catch(()=>{if(!stopped)setError('Cannot refresh runner status. Retrying.');});
    void update();
    const timer=window.setInterval(()=>void update(),5000);
    return()=>{stopped=true;window.clearInterval(timer);};
  },[operator.organizationId]);
  useEffect(()=>{if(!token)return;const timer=window.setTimeout(()=>setToken(''),Math.max(0,expires-Date.now()));return()=>window.clearTimeout(timer);},[token,expires]);
  async function enroll(){setBusy(true);setError('');try{setToken(await issueRunnerToken(operator.organizationId));setExpires(Date.now()+15*60*1000);}catch(reason){setError(reason instanceof Error?reason.message:'Enrollment failed');}finally{setBusy(false);}}
  return <section className="panel guide-section" id="runner-install">
    <h2>Install and connect a runner</h2>
    <p>Run agents on a dedicated machine you control. The packaged installer needs Java 21+, Git, and Docker with Linux containers. No source build or Maven is required.</p>
    <ol><li><a href="/downloads/forgeloop-runner.zip" download>Download runner package</a> and <a href="/downloads/forgeloop-runner.zip.sha256" download>SHA-256 checksum</a>. Extract into a private, permanent folder outside your repositories.</li>
      <li>Generate a one-time enrollment token below. It belongs only to this organization and expires after 15 minutes.</li>
      <li>On Windows, run <code>powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Runner.ps1</code> from the extracted folder. Review the script first. It prompts locally for the token, provider/model, API key, and token prices. No provider key is sent to this website.</li>
      <li>Use <code>powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Runner.ps1</code> to start work. Add <code>-StartAtLogin</code> when running the installer for optional login startup. Keep Docker running. The package README also covers Linux/macOS CLI setup.</li></ol>
    <p>Control-plane URL: <code>{window.location.origin}</code>. Installation connects and checks the runner heartbeat without making paid model requests.</p>
    {operator.role==='ADMIN'?<button className="primary" disabled={busy} onClick={()=>void enroll()}>Generate enrollment token</button>:<p>An organization administrator must generate your enrollment token.</p>}
    {token&&<div className="guide-callout"><label>One-time enrollment token<input readOnly type="password" value={token} autoComplete="off"/></label><button onClick={()=>void navigator.clipboard.writeText(token).catch(()=>setError('Clipboard unavailable; select the token field to copy manually.'))}>Copy token</button><button onClick={()=>setToken('')}>Hide token</button><small>Expires {new Date(expires).toLocaleTimeString()}. Clear your clipboard after enrollment. Hiding does not revoke an unused token.</small></div>}
    {error&&<p role="alert">{error}</p>}
    <h3>Connected runners</h3>
    {runners.length?runners.map(runner=><div className="item" key={runner.id}><div><b>{runner.name}</b><small>{runner.capabilities.join(', ')} · v{runner.version}</small></div><span>{runner.enabled&&Date.now()-Date.parse(runner.lastHeartbeatAt)<30000?'Online':'Offline'} · last heartbeat {new Date(runner.lastHeartbeatAt).toLocaleString()}</span></div>):<p>No runners enrolled yet.</p>}
  </section>;
}
