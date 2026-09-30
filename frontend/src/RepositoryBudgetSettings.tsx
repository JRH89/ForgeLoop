import { useEffect, useState } from 'react';
import { configureRepositoryBudget, type RepositoryConnection } from './api';

/** Keeps issue-triggered run requests within both the repository and organization budget boundaries. */
export default function RepositoryBudgetSettings({ item, organizationLimit, editable, onSaved }: {
  item: RepositoryConnection;
  organizationLimit: number;
  editable: boolean;
  onSaved: (item: RepositoryConnection) => void;
}) {
  const [budget, setBudget] = useState(String(item.maxBudgetUsd));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => setBudget(String(item.maxBudgetUsd)), [item.maxBudgetUsd]);

  return <form className="repository-budget-settings" onSubmit={event => {
    event.preventDefault();
    const maxBudgetUsd = Number(budget);
    if (!Number.isFinite(maxBudgetUsd) || maxBudgetUsd <= 0 || maxBudgetUsd > organizationLimit) {
      setError(`Enter an amount greater than $0 and no more than the organization limit of $${organizationLimit}.`);
      return;
    }
    setBusy(true);
    setError('');
    void configureRepositoryBudget(item.repository, maxBudgetUsd)
      .then(saved => { onSaved(saved); setBudget(String(saved.maxBudgetUsd)); })
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Unable to save repository budget'))
      .finally(() => setBusy(false));
  }}>
    <label>Maximum run budget (USD)
      <input aria-label={`Maximum run budget for ${item.repository}`} type="number" min="0.01" step="0.01"
        max={organizationLimit} value={budget} onChange={event => setBudget(event.target.value)}
        onInvalid={event => {
          event.preventDefault();
          setError(`Enter an amount greater than $0 and no more than the organization limit of $${organizationLimit}.`);
        }}
        disabled={!editable || busy}/>
    </label>
    <small>Issue-triggered runs use this repository ceiling. It must not exceed the organization limit (${organizationLimit}); existing runs keep their original budget.</small>
    {editable && <button disabled={busy}>Save repository budget</button>}
    {error && <p role="alert">{error}</p>}
  </form>;
}
