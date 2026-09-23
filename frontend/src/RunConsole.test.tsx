import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './main';

afterEach(() => vi.unstubAllGlobals());

function controlPlane(data: Record<string, unknown>) {
  return vi.fn().mockImplementation(async (_url: string, init: RequestInit) => {
    const query = (JSON.parse(String(init.body)) as { query: string }).query;
    if (query.includes('currentOperator')) return { ok: true, json: async () => ({ data: { currentOperator: { subject: 'operator', organizationId: 'local-development', role: 'ADMIN' } } }) };
    if (query.includes('repositoryConnections')) return { ok: true, json: async () => ({ data: { repositoryConnections: data.repositoryConnections ?? [] } }) };
    if (query.includes('runAnalytics')) return { ok: true, json: async () => ({ data: { runAnalytics: { totalRuns: 0, activeRuns: 0, deliveredRuns: 0, providerRequests: 0, inputTokens: 0, outputTokens: 0, knownCostMicros: 0, costCoverage: 1, modelComparisons: [], harnessComparisons: [] } } }) };
    return { ok: true, json: async () => ({ data: { featureRuns: data.featureRuns ?? [] } }) };
  });
}

it('renders the real intake queue and role-aware creation flow', async () => {
  vi.stubGlobal('fetch', controlPlane({ repositoryConnections: [] }));
  render(<App />);
  expect(await screen.findByText('Intake queue')).toBeInTheDocument();
  expect(screen.getByText(/GitHub issues carrying/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '+ New run' }));
  expect(screen.getByText('Start a delivery run')).toBeInTheDocument();
  expect(screen.getByLabelText('Authorized repository')).toBeInTheDocument();
  expect(screen.queryByText('Support Desk')).not.toBeInTheDocument();
});

it('keeps viewers read-only', async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { repositoryConnections: [] } }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { featureRuns: [] } }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { currentOperator: { subject: 'viewer', organizationId: 'acme', role: 'VIEWER' } } }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { runAnalytics: { totalRuns: 0, activeRuns: 0, deliveredRuns: 0, providerRequests: 0, inputTokens: 0, outputTokens: 0, knownCostMicros: 0, costCoverage: 1, modelComparisons: [], harnessComparisons: [] } } }) });
  vi.stubGlobal('fetch', fetch);
  render(<App />);
  expect(await screen.findByText('Intake queue')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '+ New run' })).not.toBeInTheDocument();
});
