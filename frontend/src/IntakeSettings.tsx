import { useState } from 'react';
import { configureIntake, type RepositoryConnection } from './api';

export default function IntakeSettings({item,editable,onSaved}:{item:RepositoryConnection;editable:boolean;onSaved:(item:RepositoryConnection)=>void}) {
  const [login,setLogin]=useState(item.requiredAssignee??'');
  const [requireAssignee,setRequireAssignee]=useState(item.requireAssignee??Boolean(item.requiredAssignee));
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  return <form onSubmit={event=>{event.preventDefault();setBusy(true);setError('');void configureIntake(item.repository,requireAssignee,requireAssignee?login:'').then(saved=>{onSaved(saved);setRequireAssignee(saved.requireAssignee);setLogin(saved.requiredAssignee??'');}).catch(reason=>setError(reason instanceof Error?reason.message:'Unable to save intake policy')).finally(()=>setBusy(false));}}>
    <label className="check"><input aria-label={`Wait for assignment on ${item.repository}`} type="checkbox" checked={requireAssignee} onChange={event=>setRequireAssignee(event.target.checked)} disabled={!editable}/> Wait until an issue is assigned</label>
    <label>Specific GitHub assignee (optional)<input aria-label={`Required assignee for ${item.repository}`} value={login} onChange={event=>setLogin(event.target.value)} placeholder="Login without @; blank allows any assignee" disabled={!editable||!requireAssignee}/></label>
    <small>When enabled, issues need an assignee before ForgeLoop starts work. Add a login to require that person specifically. The intake label is still required; existing runs are unaffected.</small>
    {editable&&<button disabled={busy}>Save intake setting</button>}{error&&<p role="alert">{error}</p>}
  </form>;
}
