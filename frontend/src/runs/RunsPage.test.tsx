import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RunsPage from './RunsPage';
import { loadRuns, type FeatureRun } from '../api';

vi.mock('../api', async importOriginal => ({
  ...await importOriginal<typeof import('../api')>(),
  loadRuns: vi.fn(),
}));
afterEach(() => vi.clearAllMocks());

it('loads the requested history page and resets pagination when changing archive selection', async () => {
  const runs: FeatureRun[] = Array.from({ length: 50 }, (_, index) => ({
    id: `run-${index}`, title: `Run ${index}`, sourceRef: `issue-${index}`, repository: 'acme/repo',
    state: 'CANCELLED', archived: false, createdAt: '2026-01-01T00:00:00Z', tasks: [],
    specification: 'Spec', budgetUsd: 1, spentCostMicros: 0, harnessProfile: 'GENERIC', baseBranch: 'main',
    policyRevision: 1, approved: false, gates: [], criteria: [],
  }));
  vi.mocked(loadRuns).mockResolvedValue(runs);
  const { unmount } = render(<RunsPage repositories={[]} runs={runs} operator={{ subject: 'operator', organizationId: 'org', role: 'VIEWER' }} setRuns={vi.fn()}/>);
  await waitFor(() => expect(loadRuns).toHaveBeenCalledWith(0, false));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
  await waitFor(() => expect(loadRuns).toHaveBeenCalledWith(1, false));
  expect(screen.getByText(/Page 2/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: 'Show archived runs' }));
  await waitFor(() => expect(loadRuns).toHaveBeenLastCalledWith(0, true));
  expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
  unmount();
});
