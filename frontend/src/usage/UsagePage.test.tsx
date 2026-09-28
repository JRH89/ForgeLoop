import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { FeatureRun, ProviderActivity, ProviderAttempt } from '../api';
import { usageSummary } from './usage';
import UsagePage from './UsagePage';

const now = Date.parse('2026-09-27T12:00:00Z');
function attempt(overrides: Partial<ProviderAttempt> = {}): ProviderAttempt {
  return { id: 'a', provider: 'anthropic', model: 'example', inputTokens: 100, outputTokens: 20, attemptCount: 1, estimatedCostMicros: 2_000_000, costKnown: true, outcome: 'SUCCEEDED', retryable: false, category: 'SUCCESS', recordedAt: '2026-09-27T01:00:00Z', ...overrides };
}
function run(attempts: ProviderAttempt[], overrides: Partial<FeatureRun> = {}): FeatureRun {
  return { id: 'r', repository: 'acme/app', sourceRef: 'issue-1', title: 'A change', state: 'COMPLETE', archived: true, createdAt: '2026-08-01T00:00:00Z', budgetUsd: 10, tasks: [{ providerAttempts: attempts }], ...overrides } as FeatureRun;
}
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('attributes cost to recording day and keeps archived runs in totals', () => {
  const summary = usageSummary([run([attempt()])], 7, '', now);
  expect(summary.total.knownMicros).toBe(2_000_000);
  expect(summary.daily).toHaveLength(7);
  expect(summary.daily.at(-1)?.knownMicros).toBe(2_000_000);
  expect(summary.rows).toHaveLength(1);
});

it('filters UTC boundaries, repository, and excludes invalid timestamps', () => {
  const summary = usageSummary([
    run([attempt({ recordedAt: '2026-09-20T23:59:59Z' }), attempt({ recordedAt: '2026-09-21T00:00:00Z' }), attempt({ recordedAt: '2026-09-28T00:00:00Z' }), attempt({ recordedAt: 'invalid' })]),
    run([attempt()], { repository: 'other/repo' }),
  ], 7, 'acme/app', now);
  expect(summary.total.priced).toBe(1);
});

it('does not add unknown costs to dollar totals or lose their tokens', () => {
  const summary = usageSummary([run([attempt(), attempt({ costKnown: false, estimatedCostMicros: 999_000_000 })])], 30, '', now);
  expect(summary.total).toMatchObject({ knownMicros: 2_000_000, priced: 1, unpriced: 1, tokens: 240 });
  expect(summary.models[0]).toMatchObject({ priced: 1, unpriced: 1 });
});

it('combines run and AI activity costs by date, model, and activity type', () => {
  const activity: ProviderActivity = { id: 'scan-1', repository: 'acme/app', activityType: 'REPOSITORY_SCAN', provider: 'openai', model: 'gpt-test', inputTokens: 30, outputTokens: 10, estimatedCostMicros: 500_000, costKnown: true, recordedAt: '2026-09-27T02:00:00Z' };
  const summary = usageSummary([run([attempt()])], 7, '', now, [activity]);
  expect(summary.total).toMatchObject({ knownMicros: 2_500_000, priced: 2, tokens: 160 });
  expect(summary.activityTypes.find(item => item.name === 'REPOSITORY_SCAN')).toMatchObject({ knownMicros: 500_000, tokens: 40 });
  expect(summary.models.find(item => item.name === 'openai / gpt-test')).toMatchObject({ knownMicros: 500_000, priced: 1 });
  expect(summary.activities[0]).toMatchObject({ activityType: 'REPOSITORY_SCAN', repository: 'acme/app' });
});

function mockApi(runs: FeatureRun[], activities: ProviderActivity[] = []) {
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => ({ ok: true, json: async () => ({ data: JSON.parse(init.body).query.includes('runAnalytics')
    ? { runAnalytics: { totalRuns: runs.length, activeRuns: 0, deliveredRuns: 1, providerRequests: 2 } }
    : JSON.parse(init.body).query.includes('providerActivities') ? { providerActivities: activities } : { featureRuns: runs } }) })));
}

it('shows graphs, unpriced coverage, filters, and the settings action', async () => {
  mockApi([run([attempt({ recordedAt: new Date().toISOString() }), attempt({ costKnown: false, recordedAt: new Date().toISOString() })])]);
  const onSettings = vi.fn();
  render(<UsagePage onSettings={onSettings} />);
  expect(await screen.findByRole('img', { name: /Daily estimated cost/ })).toBeInTheDocument();
  expect(screen.getByText('50%')).toBeInTheDocument();
  expect(screen.getByText(/Historical estimates are not silently rewritten/)).toBeInTheDocument();
  expect(screen.getByText(/Archived$/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Period (UTC)'), { target: { value: '7' } });
  expect(screen.getByLabelText('Period (UTC)')).toHaveValue('7');
  fireEvent.click(screen.getByRole('button', { name: 'Budget & execution settings' }));
  expect(onSettings).toHaveBeenCalledOnce();
});

it('shows repository scans in the aggregate activity cost breakdown', async () => {
  mockApi([], [{ id: 'scan-1', repository: 'acme/app', activityType: 'REPOSITORY_SCAN', provider: 'openai', model: 'gpt-test', inputTokens: 30, outputTokens: 10, estimatedCostMicros: 500_000, costKnown: true, recordedAt: new Date().toISOString() }]);
  render(<UsagePage onSettings={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: 'Cost by activity' })).toBeInTheDocument();
  expect(screen.getAllByText('Repository scans')).toHaveLength(2);
  expect(screen.getByText('AI activity costs')).toBeInTheDocument();
  expect(screen.getByText('acme/app · openai / gpt-test')).toBeInTheDocument();
});

it('shows N/A rather than zero dollars for entirely unpriced usage', async () => {
  mockApi([run([attempt({ costKnown: false, recordedAt: new Date().toISOString() })])]);
  render(<UsagePage onSettings={vi.fn()} />);
  expect(await screen.findByText('No priced usage recorded in this period.')).toBeInTheDocument();
  expect(screen.getByText('0%')).toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});

it('shows a retry action after a failed load', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  render(<UsagePage onSettings={vi.fn()} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Usage refresh failed');
  mockApi([]);
  fireEvent.click(screen.getByRole('button', { name: 'Refresh usage' }));
  expect(await screen.findByText('No runs or usage recorded for these filters.')).toBeInTheDocument();
});
