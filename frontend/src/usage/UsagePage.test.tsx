import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { FeatureRun, ProviderAttempt } from '../api';
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

function mockApi(runs: FeatureRun[]) {
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => ({ ok: true, json: async () => ({ data: JSON.parse(init.body).query.includes('runAnalytics')
    ? { runAnalytics: { totalRuns: runs.length, activeRuns: 0, deliveredRuns: 1, providerRequests: 2 } }
    : { featureRuns: runs } }) })));
}

it('shows graphs, unpriced coverage, filters, and the settings action', async () => {
  mockApi([run([attempt({ recordedAt: new Date().toISOString() }), attempt({ costKnown: false, recordedAt: new Date().toISOString() })])]);
  const onSettings = vi.fn();
  render(<UsagePage onSettings={onSettings} />);
  expect(await screen.findByRole('img', { name: /Daily estimated cost/ })).toBeInTheDocument();
  expect(screen.getByText('50%')).toBeInTheDocument();
  expect(screen.getByText(/Historical missing prices/)).toBeInTheDocument();
  expect(screen.getByText(/Archived$/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Period (UTC)'), { target: { value: '7' } });
  expect(screen.getByLabelText('Period (UTC)')).toHaveValue('7');
  fireEvent.click(screen.getByRole('button', { name: 'Budget & execution settings' }));
  expect(onSettings).toHaveBeenCalledOnce();
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
