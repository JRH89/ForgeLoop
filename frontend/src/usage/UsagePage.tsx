import { useEffect, useState } from 'react';
import { loadRunAnalytics, loadRuns, type FeatureRun, type RunAnalytics } from '../api';
import { usageSummary, usd, type UsageBucket } from './usage';
import './usage.css';

/** Read-only organization telemetry. Provider invoices remain the billing source of truth. */
export default function UsagePage({ initialAnalytics, onSettings }: { initialAnalytics?: RunAnalytics; onSettings: () => void }) {
  const [analytics, setAnalytics] = useState(initialAnalytics);
  const [runs, setRuns] = useState<FeatureRun[]>();
  const [days, setDays] = useState(30);
  const [repository, setRepository] = useState('');
  const [updated, setUpdated] = useState<number>();
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const [nextRuns, nextAnalytics] = await Promise.all([loadRuns(), loadRunAnalytics()]);
        if (!disposed) { setRuns(nextRuns); setAnalytics(nextAnalytics); setUpdated(Date.now()); setError(''); }
      } catch { if (!disposed) setError('Usage refresh failed. Any displayed data is from the last successful check.'); }
      finally { if (!disposed) timer = setTimeout(() => void poll(), 10_000); }
    }
    void poll(); return () => { disposed = true; clearTimeout(timer); };
  }, [refresh]);
  const usage = runs && usageSummary(runs, days, repository, updated);
  const count = usage ? usage.total.priced + usage.total.unpriced : 0;
  return <section className="usage-page">
    <div className="page-title"><div><p className="eyebrow">Organization usage</p><h1>Usage &amp; costs</h1><p>Track recorded model usage and estimated run costs.</p></div><button className="secondary" onClick={onSettings}>Budget &amp; execution settings</button></div>
    <p>Costs are estimates from runner-configured pricing, not provider invoices. Unpriced requests are excluded from dollar totals, never counted as free. Archived runs remain included.</p>
    <div className="usage-filters"><div><label htmlFor="usage-period">Period (UTC)</label><select id="usage-period" value={days} onChange={event => setDays(Number(event.target.value))}><option value={7}>Last 7 days</option><option value={30}>Last 30 days</option><option value={90}>Last 90 days</option></select></div>
      <div><label htmlFor="usage-repository">Repository</label><select id="usage-repository" value={repository} onChange={event => setRepository(event.target.value)}><option value="">All repositories</option>{[...new Set(runs?.map(run => run.repository))].sort().map(name => <option key={name}>{name}</option>)}</select></div>
      <button className="secondary" onClick={() => setRefresh(value => value + 1)}>Refresh usage</button></div>
    {error && <p role="alert">{error}</p>}
    <p role="status">{updated ? `${error ? 'Last successful update' : 'Updated'} ${new Date(updated).toLocaleTimeString()} · refreshes every 10 seconds` : error ? 'Usage unavailable. Retry with Refresh usage.' : 'Loading usage…'}</p>
    {usage && <>
      <section className="metrics usage-metrics" aria-label="Selected period totals">
        <article><b>{usage.total.priced ? usd(usage.total.knownMicros) : 'N/A'}</b><span>Priced estimated cost</span></article>
        <article><b>{count.toLocaleString()}</b><span>Usage records</span></article>
        <article><b>{usage.total.tokens.toLocaleString()}</b><span>Input + output tokens</span></article>
        <article><b>{count ? `${Math.round(usage.total.priced / count * 100)}%` : 'N/A'}</b><span>Pricing coverage · {usage.total.unpriced} unpriced</span></article>
      </section>
      {usage.total.unpriced > 0 && <p className="usage-warning">{usage.total.unpriced} usage records have no price. Configure input/output token rates in the desktop Provider tab for future work. Historical missing prices are not silently backfilled.</p>}
      <div className="usage-charts"><section className="panel"><h2>Daily estimated cost</h2><p>UTC recording day · priced usage only</p><DailyChart buckets={usage.daily} /><details><summary>View daily values</summary><ul>{usage.daily.filter(day => day.priced + day.unpriced > 0).map(day => <li key={day.name}>{day.name}: {day.priced ? usd(day.knownMicros) : 'N/A'} · {day.unpriced} unpriced</li>)}</ul></details></section>
        <section className="panel"><h2>Cost by model</h2>{usage.models.length ? <ul className="model-costs">{usage.models.map(model => <li key={model.name}><div><span>{model.name}</span><strong>{model.priced ? usd(model.knownMicros) : 'N/A'}</strong></div><div className="cost-track" aria-hidden="true"><i style={{ width: `${usage.total.knownMicros ? model.knownMicros / usage.total.knownMicros * 100 : 0}%` }}/></div><small>{model.tokens.toLocaleString()} tokens · {model.unpriced} unpriced</small></li>)}</ul> : <p>No model usage recorded in this period.</p>}</section></div>
      <section className="panel"><h2>Run costs in selected period</h2>{usage.rows.length ? <ul className="run-costs">{usage.rows.map(({ run, usage: row }) => <li key={run.id}><div><strong>{run.sourceRef}: {run.title}</strong><small>{run.repository} · {run.state}{run.archived ? ' · Archived' : ''}</small></div><div><strong>{row.priced ? usd(row.knownMicros) : 'N/A'}</strong><small>{row.priced + row.unpriced ? `${row.unpriced} unpriced · ${row.tokens.toLocaleString()} tokens` : 'No usage recorded'}</small><small>Run budget: {usd(run.budgetUsd * 1_000_000)}</small></div></li>)}</ul> : <p>No runs or usage recorded for these filters.</p>}</section>
    </>}
    {usage && <section className="panel"><h2>Harness comparison</h2>{usage.harnesses.length ? <ul className="run-costs">{usage.harnesses.map(harness => <li key={harness.name}><div><strong>{harness.name}</strong><small>{harness.tokens.toLocaleString()} tokens · {harness.unpriced} unpriced</small></div><strong>{harness.priced ? usd(harness.knownMicros) : 'N/A'}</strong></li>)}</ul> : <p>No harness usage recorded in this period.</p>}</section>}
    {analytics && <section className="panel"><h2>All-time delivery overview</h2><p>This overview covers all repositories and is not changed by the filters above.</p><p>{analytics.totalRuns} runs · {analytics.activeRuns} active · {analytics.deliveredRuns} delivered · {analytics.providerRequests} provider requests</p></section>}
  </section>;
}

function DailyChart({ buckets }: { buckets: UsageBucket[] }) {
  if (!buckets.some(bucket => bucket.priced)) return <p>No priced usage recorded in this period.</p>;
  const peak = Math.max(0, ...buckets.map(bucket => bucket.knownMicros));
  const max = Math.max(1, peak);
  const width = 600, height = 160, step = width / buckets.length;
  return <>
    <svg className="daily-cost-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Daily estimated cost in US dollars; exact values available below">
      <line x1="0" y1="159" x2="600" y2="159" stroke="currentColor"/>
      {buckets.map((bucket, index) => {
        const barHeight = bucket.knownMicros / max * 150;
        return <rect key={bucket.name} x={index * step + 1} y={159 - barHeight} width={Math.max(1, step - 2)} height={barHeight} fill="#46dfaa"><title>{bucket.name}: {bucket.priced ? usd(bucket.knownMicros) : 'No priced usage'}; {bucket.unpriced} unpriced</title></rect>;
      })}
    </svg>
    <div className="chart-axis"><span>{buckets[0].name}</span><span>Peak {usd(peak)}</span><span>{buckets.at(-1)?.name}</span></div>
  </>;
}
