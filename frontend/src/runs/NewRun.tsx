import { useEffect, useState, type FormEvent } from 'react';
import { loadPlatformConfiguration, submitFeature, type FeatureRun, type RepositoryConnection } from '../api';

export default function NewRun({
  repositories,
  onAdded,
}: {
  repositories: RepositoryConnection[];
  onAdded: (run: FeatureRun) => void;
}) {
  const [form, setForm] = useState({
    repository: "",
    sourceRef: "",
    title: "",
    specification: "",
  });
  const [organizationLimit, setOrganizationLimit] = useState<number>();
  useEffect(() => { void loadPlatformConfiguration().then(value => setOrganizationLimit(value.policy.maxRunBudgetUsd)).catch(() => setError('Unable to load budget policy. Try again.')); }, []);
  const selectedRepository = repositories.find(item => item.repository === form.repository);
  const budgetUsd = organizationLimit !== undefined && selectedRepository ? Math.min(organizationLimit, selectedRepository.maxBudgetUsd) : undefined;
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (budgetUsd === undefined) throw new Error("Select a repository with a configured budget.");
      onAdded(await submitFeature({ ...form, budgetUsd }));
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Unable to create run",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="panel form" onSubmit={(event) => void submit(event)}>
      <h2>Start a delivery run</h2>
      <div className="form-grid">
        <label>
          Authorized repository
          <select
            aria-label="Authorized repository"
            required
            value={form.repository}
            onChange={(event) =>
              setForm({ ...form, repository: event.target.value })
            }
          >
            <option value="">Select repository</option>
            {repositories
              .filter((item) => item.enabled)
              .map((item) => (
                <option key={item.id}>{item.repository}</option>
              ))}
          </select>
        </label>
        <label>
          Issue or source reference
          <input
            aria-label="Issue or source reference"
            required
            value={form.sourceRef}
            onChange={(event) =>
              setForm({ ...form, sourceRef: event.target.value })
            }
          />
        </label>
      </div>
      <label>
        Title
        <input
          aria-label="Title"
          required
          value={form.title}
          onChange={(event) => setForm({ ...form, title: event.target.value })}
        />
      </label>
      <label>
        Acceptance-focused specification
        <textarea
          aria-label="Specification"
          required
          value={form.specification}
          onChange={(event) =>
            setForm({ ...form, specification: event.target.value })
          }
        />
      </label>
      <p role="status">{budgetUsd !== undefined ? `Run limit: $${budgetUsd}. Inherited from Settings; no separate run budget is needed.` : 'Select a repository to see its run limit.'}</p>
      <button
        className="primary"
        disabled={busy || budgetUsd === undefined || !selectedRepository?.enabled}
      >
        {busy ? "Submitting…" : "Create delivery run"}
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
