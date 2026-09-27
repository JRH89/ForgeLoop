import { useState } from 'react';
import { LockKeyhole, ShieldCheck } from 'lucide-react';
import favicon from './assets/favicon.png';
import './runner-pairing.css';
import { approveRunnerPairing, loadOperator } from './api';

/** The URL contains only a proof hash, never the secret required to claim enrollment. */
export default function RunnerPairingPage() {
  const params=new URLSearchParams(window.location.hash.slice(1));
  const challenge=params.get('challenge')??'';
  const name=params.get('name')??'';
  const [confirmed,setConfirmed]=useState(false);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [approved,setApproved]=useState(false);
  const valid=/^[a-f0-9]{64}$/.test(challenge)&&name.length>0&&name.length<=100;
  async function approve() {
    setBusy(true);setMessage('');
    try {
      const operator=await loadOperator();
      if(operator.role!=='ADMIN')throw new Error('An organization administrator must approve this runner.');
      await approveRunnerPairing(challenge,name);setApproved(true);
    } catch {setMessage('Approval failed. Sign in as an organization administrator, then return to this tab and retry. If already approved or expired, reconnect from the desktop app.');}
    finally {setBusy(false);}
  }
  return <main className="runner-pairing">
    <a className="pairing-brand" href="/"><img src={favicon} alt=""/>ForgeLoop</a>
    <section className="pairing-card" aria-labelledby="pairing-title">
      <div className="pairing-symbol" aria-hidden="true"><ShieldCheck size={28}/></div>
      <p className="pairing-eyebrow">Secure runner connection</p>
      <h1 id="pairing-title">Connect your runner</h1>
      {!valid ? <div className="pairing-notice" role="alert">Invalid pairing request. Start Connect from the ForgeLoop Runner app.</div>
        : approved ? <div className="pairing-notice pairing-success" role="status">Approved. Return to the desktop app to finish setup. No paid work has started.</div>
        : <>
          <p className="pairing-intro">Only approve a request you just started on a computer you trust. This grants that runner access to your organization’s eligible repository tasks.</p>
          <dl className="pairing-details">
            <div><dt>Runner</dt><dd>{name}</dd></div>
            <div><dt>Verification fingerprint</dt><dd><code>{challenge.slice(0,12)}</code></dd></div>
          </dl>
          <p className="pairing-hint">Match this fingerprint with the desktop app before continuing.</p>
          <p className="pairing-signin"><a href="/oauth2/authorization/github" target="_blank" rel="noopener noreferrer">Sign in with GitHub in a separate tab</a>, then return here.</p>
          <label className="pairing-confirm"><input type="checkbox" checked={confirmed} onChange={event=>setConfirmed(event.target.checked)}/><span>I started this request and the fingerprint matches.</span></label>
          <button className="primary pairing-approve" disabled={!confirmed||busy} onClick={()=>void approve()}>{busy ? 'Approving…' : 'Approve runner'}</button>
          {message&&<div className="pairing-notice" role="alert">{message}</div>}
        </>}
      <p className="pairing-footnote"><LockKeyhole size={14} aria-hidden="true"/>Connecting does not start paid work. You control when the runner starts.</p>
    </section>
    <a className="pairing-help" href="/docs">Need help? Read the setup guide →</a>
  </main>;
}
