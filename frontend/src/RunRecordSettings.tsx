import { useState } from 'react';
import { configureRunRecord, type RepositoryConnection } from './api';

/** Makes the content-capture opt-in explicit at the repository's admin boundary. */
export default function RunRecordSettings({ item, editable, onSaved }: {
  item: RepositoryConnection;
  editable: boolean;
  onSaved: (item: RepositoryConnection) => void;
}) {
  const [enabled, setEnabled] = useState(Boolean(item.runRecord));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  return <form className="repository-run-record-settings" onSubmit={event => {
    event.preventDefault();
    setBusy(true);
    setError('');
    void configureRunRecord(item.repository, enabled)
      .then(saved => { onSaved(saved); setEnabled(Boolean(saved.runRecord)); })
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to save run-record setting'))
      .finally(() => setBusy(false));
  }}>
    <label className="check">
      <input aria-label={`Save run records for ${item.repository}`} type="checkbox" checked={enabled}
        onChange={event => setEnabled(event.target.checked)} disabled={!editable || busy}/>
      Save detailed run records
    </label>
    <small>When enabled, prompts, provider responses, repository context, and execution evidence are uploaded as run artifacts. Organization operators can download them; enable this only if that content is appropriate to retain.</small>
    {editable && <button disabled={busy}>Save run-record setting</button>}
    {error && <p role="alert">{error}</p>}
  </form>;
}
