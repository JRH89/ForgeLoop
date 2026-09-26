import { useState } from 'react';
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
  return <main className="guide panel guide-section"><h1>Connect your runner</h1>
    {!valid?<p role="alert">Invalid pairing request. Start Connect from the ForgeLoop Runner app.</p>:approved?<p role="status">Approved. Return to the desktop app to finish setup. No paid work has started.</p>:<>
      <p>Only approve a request you just started on a computer you trust. This grants that runner access to your organization’s eligible repository tasks.</p>
      <p>Runner: <strong>{name}</strong></p><p>Match this fingerprint with the desktop app: <code>{challenge.slice(0,12)}</code></p>
      <p><a href="/oauth2/authorization/github" target="_blank" rel="noopener noreferrer">Sign in with GitHub in a separate tab</a>, then return here.</p>
      <label><input type="checkbox" checked={confirmed} onChange={event=>setConfirmed(event.target.checked)}/> I started this request and the fingerprint matches.</label>
      <button className="primary" disabled={!confirmed||busy} onClick={()=>void approve()}>Approve runner</button>
      {message&&<p role="alert">{message}</p>}
    </>}
  </main>;
}
