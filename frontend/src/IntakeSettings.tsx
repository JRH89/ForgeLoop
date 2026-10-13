import { useState } from 'react';
import { configureIntake, type RepositoryConnection } from './api';

export default function IntakeSettings({item,editable,onSaved}:{item:RepositoryConnection;editable:boolean;onSaved:(item:RepositoryConnection)=>void}) {
  const [login,setLogin]=useState(item.requiredAssignee??'');
  const [requireAssignee,setRequireAssignee]=useState(item.requireAssignee??Boolean(item.requiredAssignee));
  const [requireLabel,setRequireLabel]=useState(item.requireLabel !== false);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  return <form className="repository-setting repository-intake-settings" onSubmit={event=>{event.preventDefault();if(!requireLabel && item.requireLabel !== false && !window.confirm('Bypass the intake label? New eligible issues can start paid work without a label, including issues created by Issue Chat or approved scan proposals. Assignee rules and budgets still apply.'))return;setBusy(true);setError('');void configureIntake(item.repository,requireAssignee,requireAssignee?login:'',requireLabel).then(saved=>{onSaved(saved);setRequireLabel(saved.requireLabel !== false);setRequireAssignee(saved.requireAssignee);setLogin(saved.requiredAssignee??'');}).catch(reason=>setError(reason instanceof Error?reason.message:'Unable to save intake policy')).finally(()=>setBusy(false));}}>
    <div className="repository-setting-heading"><h4>Issue intake</h4><p>Choose which issues are eligible to start a run.</p></div>
    <label className="check"><input aria-label={`Require intake label on ${item.repository}`} type="checkbox" checked={requireLabel} onChange={event=>setRequireLabel(event.target.checked)} disabled={!editable}/> Require intake label ({item.issueLabel || 'forgeloop'})</label>
    {!requireLabel && <small>Label bypass is enabled. New eligible issues can start paid work without a label.</small>}
    <label className="check"><input aria-label={`Wait for assignment on ${item.repository}`} type="checkbox" checked={requireAssignee} onChange={event=>setRequireAssignee(event.target.checked)} disabled={!editable}/> Wait for an assignee</label>
    <label>Specific GitHub assignee <span>Optional · leave blank to allow any assignee</span><input aria-label={`Required assignee for ${item.repository}`} value={login} onChange={event=>setLogin(event.target.value)} placeholder="GitHub username (without @)" disabled={!editable||!requireAssignee}/></label>
    <small>Assignment and label requirements are independent. Add a login to require that person specifically. Existing runs are unaffected; changing this setting does not rescan existing issues.</small>
    {editable&&<button type="submit" className="secondary" disabled={busy}>{busy?'Saving…':'Save intake settings'}</button>}{error&&<p role="alert">{error}</p>}
  </form>;
}
