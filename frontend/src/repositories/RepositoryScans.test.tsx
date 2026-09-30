import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RepositoryScans from './RepositoryScans';

const finding = { id: 'finding-1', severity: 'HIGH', title: 'Reject invalid values', description: 'Empty values pass validation.', impact: 'Invalid records can be stored.', evidence: 'The validator accepts an empty string.', affectedFiles: ['src/Validator.java'], acceptanceCriteria: ['Reject empty input.'], issueNumber: null, issueUrl: null, proposal: null };
const scan = { id: 'scan-1', repository: 'owner/repo', baseBranch: 'main', status: 'COMPLETE', requestedBy: 'admin', createdAt: '2026-01-01T00:00:00Z', commitSha: 'a'.repeat(40), provider: 'openai', model: 'model', inputTokens: 100, outputTokens: 20, estimatedCostMicros: 1200, costKnown: true, failureSummary: null, findings: [finding] };
const proposal = { id: 'proposal-1', repository: 'owner/repo', status: 'READY', createdAt: '2026-01-01T00:01:00Z', startedAt: '2026-01-01T00:01:01Z', completedAt: '2026-01-01T00:01:02Z', failureSummary: null, proposedTitle: 'Generated issue title', proposedBody: 'Generated issue description.', acceptanceCriteria: ['Generated check.'], provider: 'openai', model: 'test-model', inputTokens: 200, outputTokens: 80, estimatedCostMicros: 1000, costKnown: true, issueNumber: null, issueUrl: null };
afterEach(() => vi.unstubAllGlobals());

it('requires an explicit confirmation before starting a potentially paid scan', async () => {
  const fetch = vi.fn(async (_url: string, init: RequestInit) => ({ ok: true, json: async () => JSON.parse(String(init.body)).query.includes('repositoryScans')
    ? { data: { repositoryScans: [] } } : { data: { requestRepositoryScan: { ...scan, status: 'PENDING', findings: [] } } } }));
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin />);
  fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
  expect(await screen.findByText(/Provider charges may apply/)).toBeVisible();
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Start manual scan' }));
  expect(await screen.findByText('PENDING')).toBeVisible();
});

it('shows a safe actionable message instead of exposing the Hibernate fetch exception', async () => {
  const internalError = 'org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags';
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ errors: [{ message: internalError }] }) })));
  render(<RepositoryScans repository="owner/repo" isAdmin />);

  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent('The control plane could not load repository scan data.');
  expect(alert).not.toHaveTextContent('MultipleBagFetchException');
});

it('queues issue drafting only after the administrator chooses the extra model call', async () => {
  const pending = { ...proposal, status: 'PENDING', proposedTitle: null, proposedBody: null, acceptanceCriteria: [], model: null, provider: null, costKnown: false };
  const fetch = vi.fn(async (_url: string, init: RequestInit) => {
    const query = JSON.parse(String(init.body)).query as string;
    if (query.includes('repositoryScans')) return { ok: true, json: async () => ({ data: { repositoryScans: [scan] } }) };
    return { ok: true, json: async () => ({ data: { requestRepositoryIssueProposal: pending } }) };
  });
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin />);
  expect(await screen.findByText('Reject invalid values')).toBeVisible();
  expect(screen.getByText(/additional provider call/)).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Generate issue draft · extra model call' }));
  expect(await screen.findByText('Issue proposal · PENDING')).toBeVisible();
  expect(fetch).toHaveBeenCalledTimes(2);
  expect(String(JSON.parse(String(fetch.mock.calls[1][1]?.body)).query)).toContain('requestRepositoryIssueProposal');
  expect(screen.queryByRole('button', { name: 'Approve & create GitHub issue' })).not.toBeInTheDocument();
});

it('lets an administrator edit the generated spec and publish only after explicit approval', async () => {
  const readyScan = { ...scan, findings: [{ ...finding, proposal }] };
  const approved = { ...proposal, status: 'APPROVED', issueNumber: 7, issueUrl: 'https://github.com/owner/repo/issues/7' };
  const fetch = vi.fn(async (_url: string, init: RequestInit) => {
    const request = JSON.parse(String(init.body)) as { query: string; variables: Record<string, unknown> };
    if (request.query.includes('repositoryScans')) return { ok: true, json: async () => ({ data: { repositoryScans: [readyScan] } }) };
    return { ok: true, json: async () => ({ data: { approveRepositoryIssueProposal: approved } }) };
  });
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin />);
  expect(await screen.findByDisplayValue('Generated issue title')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Issue title'), { target: { value: 'Reviewed and edited title' } });
  fireEvent.click(screen.getByRole('button', { name: 'Approve & create GitHub issue' }));
  expect(await screen.findByRole('link', { name: 'GitHub issue #7' })).toHaveAttribute('href', 'https://github.com/owner/repo/issues/7');
  expect(JSON.parse(String(fetch.mock.calls[1][1]?.body)).variables).toMatchObject({ proposalId: 'proposal-1', title: 'Reviewed and edited title' });
});

it('allows rejection without issuing a GitHub write', async () => {
  const readyScan = { ...scan, findings: [{ ...finding, proposal }] };
  const rejected = { ...proposal, status: 'REJECTED' };
  const fetch = vi.fn(async (_url: string, init: RequestInit) => ({ ok: true, json: async () => JSON.parse(String(init.body)).query.includes('repositoryScans')
    ? { data: { repositoryScans: [readyScan] } } : { data: { rejectRepositoryIssueProposal: rejected } } }));
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin />);
  expect(await screen.findByDisplayValue('Generated issue title')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Reject draft' }));
  expect(await screen.findByText('You rejected this draft. You can generate a new one if needed.')).toBeVisible();
  expect(JSON.parse(String(fetch.mock.calls[1][1]?.body)).query).toContain('rejectRepositoryIssueProposal');
});
