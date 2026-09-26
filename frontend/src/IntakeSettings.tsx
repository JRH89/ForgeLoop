import { useState } from 'react';
import { configureIntake, type RepositoryConnection } from './api';

export default function IntakeSettings({item,editable,onSaved}:{item:RepositoryConnection;editable:boolean;onSaved:(item:RepositoryConnection)=>void}) {
  const [login,setLogin]=useState(item.requiredAssignee??'');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  return <form onSubmit={event=>{event.preventDefault();setBusy(true);setError('');void configureIntake(item.repository,login).then(onSaved).catch(reason=>setError(reason instanceof Error?reason.message:'Unable to save intake policy')).finally(()=>setBusy(false));}}>
    <label>Required GitHub assignee<input aria-label={`Required assignee for ${item.repository}`} value={login} onChange={event=>setLogin(event.target.value)} placeholder="Login without @; blank disables" disabled={!editable}/></label>
    <small>When set, both the intake label and this assignee are required. Use a login GitHub allows you to assign in this repository. Applies to future intake; does not stop existing runs.</small>
    {editable&&<button disabled={busy}>Save intake setting</button>}{error&&<p role="alert">{error}</p>}
  </form>;
}
