import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './main';

afterEach(() => vi.unstubAllGlobals());

function controlPlane(data: Record<string, unknown>) {
  return vi.fn().mockImplementation(async (_url: string, init: RequestInit) => {
    const query = (JSON.parse(String(init.body)) as { query: string }).query;
    if (query.includes('currentOperator')) return { ok: true, json: async () => ({ data: { currentOperator: { subject: 'operator', organizationId: 'local-development', role: 'ADMIN' } } }) };
    if (query.includes('repositoryConnections')) return { ok: true, json: async () => ({ data: { repositoryConnections: data.repositoryConnections ?? [] } }) };
    if (query.includes('runAnalytics')) return { ok: true, json: async () => ({ data: { runAnalytics: { totalRuns: 0, activeRuns: 0, deliveredRuns: 0, providerRequests: 0, inputTokens: 0, outputTokens: 0, knownCostMicros: 0, costCoverage: 1, modelComparisons: [], harnessComparisons: [] } } }) };
    if (query.includes('organizationPolicy')) return { ok: true, json: async () => ({ data: { organizationPolicy: { organizationId: 'local-development', maxRunBudgetUsd: 100, maxParallelTasks: 4, allowedProviders: ['anthropic'], requireHumanApproval: true, autoMergeEnabled: false, revision: 2 }, harnessDefinitions: [{ id: 'h1', organizationId: 'local-development', name: 'FULL_STACK', description: 'Plan, implement, verify, and review', allowedRoles: ['PLANNER', 'BACKEND', 'FRONTEND', 'REVIEW'], defaultAttemptBudget: 2, enabled: true, revision: 1 }], localMcpConfigurations: [] } }) };
    return { ok: true, json: async () => ({ data: { featureRuns: data.featureRuns ?? [] } }) };
  });
}

it('separates archived runs and shows missing prices rather than zero cost',async()=>{
  const createdAt=new Date().toISOString();
  vi.stubGlobal('fetch',controlPlane({featureRuns:[
    {id:'active',title:'Unpriced completed run',sourceRef:'issue-1',repository:'org/repo',state:'COMPLETE',createdAt,archived:false,spentCostMicros:0,tasks:[{state:'VERIFIED',providerAttempts:[{costKnown:false}]}]},
    {id:'archive',title:'Retained archive',sourceRef:'issue-2',repository:'org/repo',state:'CANCELLED',createdAt,archived:true,tasks:[]}
  ]}));
  render(<App/>);
  expect(await screen.findByText('Unpriced completed run')).toBeInTheDocument();
  expect(screen.getByText('Cost unavailable — configure model pricing')).toBeInTheDocument();
  expect(screen.queryByText('Retained archive')).not.toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('Show archived runs'));
  expect(screen.getByText('Retained archive')).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Restore'})).toBeInTheDocument();
});

it('refreshes the queue without selecting a run',async()=>{
  const data:Record<string,unknown>={featureRuns:[]};
  vi.stubGlobal('fetch',controlPlane(data));render(<App/>);
  await screen.findByText('Intake queue');
  data.featureRuns=[{id:'new',title:'Fresh webhook run',sourceRef:'issue-3',repository:'org/repo',state:'PLANNING',createdAt:new Date().toISOString(),tasks:[]}];
  await waitFor(()=>expect(screen.getByText('Fresh webhook run')).toBeInTheDocument(),{timeout:4000});
});

it('renders the real intake queue and role-aware creation flow', async () => {
  vi.stubGlobal('fetch', controlPlane({ repositoryConnections: [] }));
  render(<App />);
  expect(await screen.findByText('Intake queue')).toBeInTheDocument();
  expect(screen.getByText(/GitHub issues carrying/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '+ New Run' }));
  expect(screen.getByText('Start a delivery run')).toBeInTheDocument();
  expect(screen.getByLabelText('Authorized repository')).toBeInTheDocument();
  expect(screen.queryByText('Support Desk')).not.toBeInTheDocument();
});

it('filters the dashboard table and shows task verification progress', async () => {
  const createdAt = new Date().toISOString();
  vi.stubGlobal('fetch', controlPlane({
    repositoryConnections: [],
    featureRuns: [
      { id: 'running', repository: 'acme/api', sourceRef: '#41', title: 'Export monthly report', state: 'EXECUTING', createdAt, tasks: [{ state: 'VERIFIED' }, { state: 'RUNNING' }] },
      { id: 'ready', repository: 'acme/web', sourceRef: '#42', title: 'Improve onboarding', state: 'READY_FOR_REVIEW', publication: { pullRequestState: 'MERGED' }, createdAt, tasks: [{ state: 'VERIFIED' }] },
      { id: 'failed', repository: 'acme/worker', sourceRef: '#43', title: 'Upgrade dependencies', state: 'FAILED', createdAt, tasks: [{ state: 'FAILED' }] },
    ],
  }));
  render(<App />);

  expect(await screen.findByRole('table')).toBeInTheDocument();
  expect(screen.getByText('50%')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /Succeeded/ }));
  expect(screen.getByText('Improve onboarding')).toBeInTheDocument();
  expect(screen.getByText('COMPLETE')).toBeInTheDocument();
  expect(screen.queryByText('Upgrade dependencies')).not.toBeInTheDocument();
});

it('shows persisted harness and runner-local MCP configuration', async () => {
  vi.stubGlobal('fetch', controlPlane({ repositoryConnections: [] }));
  render(<App />);
  await screen.findByText('Intake queue');
  fireEvent.click(screen.getByRole('button', { name: /Harness & policy/ }));
  expect(await screen.findByText('Execution policy')).toBeInTheDocument();
  expect(screen.getByText('FULL_STACK')).toBeInTheDocument();
  expect(screen.getByText('No local MCP context routes configured.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Create harness' })).toBeInTheDocument();
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

it('provides an in-app operator guide as the fifth navigation item', async () => {
  vi.stubGlobal('fetch', controlPlane({ repositoryConnections: [] }));
  render(<App />);
  await screen.findByText('Intake queue');
  const guide = screen.getByRole('button', { name: /User guide/ });
  expect(guide).toBeInTheDocument();
  fireEvent.click(guide);
  expect(screen.getByRole('heading', { name: 'Using ForgeLoop' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'What every setting does' })).toBeInTheDocument();
  expect(screen.getByText('Auto-merge', { selector: 'dt' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'When something does not move' })).toBeInTheDocument();
});
