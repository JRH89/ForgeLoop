import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import OnboardingPage from './OnboardingPage';
import { configurationWarnings, newIssueUrl, recentRunners, runExplanation } from './readiness';
import type { FeatureRun, PlatformConfiguration, RepositoryConnection, Runner } from '../api';

const repository: RepositoryConnection = { id: 'repo', repository: 'acme/project', installationId: 1, enabled: true, defaultBranch: 'main', issueLabel: 'forgeloop', requiredAssignee: 'owner', harnessProfile: 'JVM_REACT', requiredGates: ['compile'], maxBudgetUsd: 5, policyRevision: 1 };
const config: PlatformConfiguration = { policy: { organizationId: 'org', maxRunBudgetUsd: 10, maxParallelTasks: 2, allowedProviders: ['ANTHROPIC'], requireHumanApproval: true, autoMergeEnabled: false, revision: 1 }, harnesses: [{ id: 'h', organizationId: 'org', name: 'JVM_REACT', description: '', allowedRoles: ['BACKEND'], defaultAttemptBudget: 2, enabled: true, revision: 1 }], mcp: [] };
const runner: Runner = { id: 'r', name: 'Desktop', version: '1', enabled: true, lastHeartbeatAt: new Date().toISOString(), capabilities: ['BACKEND'] };
const operator = { subject: 'me', organizationId: 'org', role: 'ADMIN' as const };

function mockApi(repositories: RepositoryConnection[] = [repository]) {
  const fetcher = vi.fn(async (_url: string, init: RequestInit) => {
    const { query } = JSON.parse(String(init.body));
    if (query.includes('mutation')) throw new Error('Setup must be read-only');
    const data = query.includes('issueIntakeCheck') ? { issueIntakeCheck: { eligible: false, reasons: ['Add the required label: forgeloop', 'Assign the issue to: owner'], checkedAt: new Date().toISOString() } }
      : query.includes('repositoryConnections') ? { repositoryConnections: repositories }
      : query.includes('runners(') ? { runners: [runner] }
      : query.includes('organizationPolicy') ? { organizationPolicy: config.policy, harnessDefinitions: config.harnesses, localMcpConfigurations: [] }
      : { featureRuns: [] };
    return { ok: true, json: async () => ({ data }) };
  });
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('walks a fresh workspace through all five steps without provider calls', async () => {
  const fetcher = mockApi([]);
  render(<OnboardingPage operator={operator} navigate={vi.fn()} />);
  expect(await screen.findByText('No enabled repository connection was found.', { exact: false })).toBeInTheDocument();
  expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(6);
  expect(screen.getByRole('link', { name: 'Install or manage ForgeLoop GitHub App' })).toHaveAttribute('href', '/api/github/app/install');
  expect(screen.queryByRole('button', { name: 'Check issue intake' })).not.toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(4);
  expect(fetcher.mock.calls.every(([url]) => url === '/graphql')).toBe(true);
});

it('checks an issue only on demand and explains missing labels and assignees', async () => {
  const fetcher = mockApi();
  render(<OnboardingPage operator={operator} navigate={vi.fn()} />);
  await screen.findByRole('button', { name: 'Check issue intake' });
  expect(fetcher).toHaveBeenCalledTimes(4);
  fireEvent.change(screen.getByLabelText('Existing GitHub issue number'), { target: { value: '7' } });
  fireEvent.click(screen.getByRole('button', { name: 'Check issue intake' }));
  expect(await screen.findByText('Add the required label: forgeloop')).toBeInTheDocument();
  expect(screen.getByText('Assign the issue to: owner')).toBeInTheDocument();
  expect(JSON.parse(String(fetcher.mock.calls[4][1].body)).variables).toEqual({ repository: 'acme/project', issueNumber: 7 });
  fireEvent.change(screen.getByLabelText('Existing GitHub issue number'), { target: { value: '8' } });
  expect(screen.queryByText('Add the required label: forgeloop')).not.toBeInTheDocument();
});

it('does not offer admin installation to viewers and keeps local key checks explicit', async () => {
  mockApi();
  render(<OnboardingPage operator={{ ...operator, role: 'VIEWER' }} navigate={vi.fn()} />);
  await screen.findByLabelText('Repository');
  expect(screen.queryByRole('link', { name: 'Install or manage ForgeLoop GitHub App' })).not.toBeInTheDocument();
  expect(screen.getByText(/The website cannot verify your local key/)).toBeInTheDocument();
});

it('shows an actionable error and supports retry instead of false readiness', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  render(<OnboardingPage operator={operator} navigate={vi.fn()} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Readiness could not be refreshed');
  expect(screen.queryByText(/configuration checks pass/)).not.toBeInTheDocument();
  mockApi();
  fireEvent.click(screen.getByRole('button', { name: 'Refresh readiness' }));
  await screen.findByLabelText('Repository');
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
});

it('distinguishes recent contact, stale timestamps, and disabled runners', () => {
  const now = Date.now();
  expect(recentRunners([runner], now)).toHaveLength(1);
  expect(recentRunners([{ ...runner, lastHeartbeatAt: 'bad-date' }, { ...runner, enabled: false }, { ...runner, lastHeartbeatAt: new Date(now - 61_000).toISOString() }], now)).toHaveLength(0);
});

it('flags missing harnesses and incompatible budgets', () => {
  expect(configurationWarnings(repository, config)).toEqual([]);
  expect(configurationWarnings({ ...repository, maxBudgetUsd: 20 }, { ...config, harnesses: [] })).toHaveLength(2);
});

it('explains approval, blocked tasks, budget exhaustion, and uncertain heartbeat state', () => {
  const run = { state: 'READY_FOR_REVIEW', approved: false, tasks: [], budgetUsd: 5, spentCostMicros: 0 } as unknown as FeatureRun;
  expect(runExplanation(run, [])).toContain('human approval');
  expect(runExplanation({ ...run, state: 'BLOCKED' }, [])).toContain('Needs attention');
  expect(runExplanation({ ...run, state: 'RUNNING', spentCostMicros: 5_000_000 }, [])).toContain('budget');
  expect(runExplanation({ ...run, state: 'RUNNING' }, [])).toContain('No enabled runner');
  expect(runExplanation({ ...run, state: 'RUNNING' }, [{ ...runner, lastHeartbeatAt: 'bad' }])).toContain('active work can delay');
  expect(runExplanation({ ...run, state: 'QUEUED', tasks: [{ state: 'PENDING', requiredCapability: 'browser' } as FeatureRun['tasks'][number]] }, [runner])).toContain('missing from enabled runners: browser');
});

it('builds a safe issue draft without automatically triggering intake', () => {
  const url = new URL(newIssueUrl('acme/project')!);
  expect(url.hostname).toBe('github.com');
  expect(url.searchParams.has('labels')).toBe(false);
  expect(url.searchParams.get('body')).toContain('Acceptance criteria');
  expect(newIssueUrl('../bad?redirect=elsewhere')).toBeUndefined();
});
