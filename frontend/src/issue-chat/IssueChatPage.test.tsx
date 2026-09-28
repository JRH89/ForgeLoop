import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import IssueChatPage from './IssueChatPage';

const repository = { id: 'repo-1', repository: 'acme/app', enabled: true } as never;
const operator = { subject: 'operator-1', organizationId: 'org-1', role: 'ADMIN' } as const;
const base = { id: 'chat-1', repository: 'acme/app', createdAt: '2026-09-28T12:00:00Z', updatedAt: '2026-09-28T12:01:00Z', failureSummary: null, draftTitle: null, draftBody: null, acceptanceCriteria: [], provider: null, model: null, inputTokens: 0, outputTokens: 0, estimatedCostMicros: 0, costKnown: false, issueNumber: null, issueUrl: null, messages: [{ role: 'USER', content: 'Add a filter to runs.', createdAt: '2026-09-28T12:00:00Z' }] };
const ready = { ...base, status: 'READY', draftTitle: 'Filter runs by status', draftBody: 'Add a status filter to the Runs view.', acceptanceCriteria: ['The selected status filters visible runs.'], provider: 'openai', model: 'gpt-test', inputTokens: 100, outputTokens: 50, estimatedCostMicros: 2500, costKnown: true, messages: [...base.messages, { role: 'ASSISTANT', content: 'I drafted a testable issue.', createdAt: '2026-09-28T12:01:00Z' }] };
const created = { ...ready, status: 'ISSUE_CREATED', issueNumber: 18, issueUrl: 'https://github.com/acme/app/issues/18' };
const delivery = { id: 'run-1', repository: 'acme/app', sourceRef: 'issue-18', title: 'Filter runs by status', state: 'READY_FOR_REVIEW', approved: false, gates: [] };
const publication = { repository: 'acme/app', pullRequestNumber: 27, pullRequestState: 'OPEN', branch: 'forgeloop/issue-18', headSha: 'abc123', deliveredAt: '2026-09-28T12:05:00Z', autoMergeRequested: false, mergedAt: null, mergeSha: null };

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, '', '/'); });

it('lets an administrator refine a runner draft before explicitly creating an unassigned issue', async () => {
  vi.stubGlobal('confirm', vi.fn(() => true));
  const fetch = vi.fn(async (_url: string, init: RequestInit) => {
    const request = JSON.parse(String(init.body)) as { query: string; variables: Record<string, unknown> };
    if (request.query.includes('issueConversations(')) return { ok: true, json: async () => ({ data: { issueConversations: [] } }) };
    if (request.query.includes('startIssueConversation')) return { ok: true, json: async () => ({ data: { startIssueConversation: { ...base, status: 'PENDING' } } }) };
    if (request.query.includes('issueConversation(')) return { ok: true, json: async () => ({ data: { issueConversation: ready } }) };
    if (request.query.includes('createIssueFromConversation')) return { ok: true, json: async () => ({ data: { createIssueFromConversation: created } }) };
    if (request.query.includes('featureRuns')) return { ok: true, json: async () => ({ data: { featureRuns: [delivery] } }) };
    if (request.query.includes('featureRunEvidence')) return { ok: true, json: async () => ({ data: { featureRunEvidence: [], featureRunReviewEvidence: [], featureRunArtifacts: [], featureRunEvents: [], featureRunEscalations: [], featureRunAuditEvents: [], featureRunPublication: publication } }) };
    return { ok: true, json: async () => ({ data: {} }) };
  });
  vi.stubGlobal('fetch', fetch);
  render(<IssueChatPage repositories={[repository]} operator={operator} onRepositories={vi.fn()} />);

  fireEvent.change(screen.getByLabelText('Describe the issue'), { target: { value: 'Add a filter to runs.' } });
  fireEvent.click(screen.getByRole('button', { name: 'Start issue chat' }));
  expect(await screen.findByText('I drafted a testable issue.')).toBeVisible();
  expect(screen.getByText(/Messages and drafts are saved/)).toBeVisible();
  expect(await screen.findByDisplayValue('Filter runs by status')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Reviewed: filter runs by status' } });
  fireEvent.change(screen.getByLabelText(/Acceptance criteria/), { target: { value: 'Selected status hides nonmatching runs.' } });
  fireEvent.click(screen.getByRole('button', { name: 'Review and create GitHub issue' }));

  expect(await screen.findByRole('link', { name: '#18 on GitHub' })).toHaveAttribute('href', created.issueUrl);
  expect(await screen.findByRole('link', { name: 'View pull request #27' })).toHaveAttribute('href', 'https://github.com/acme/app/pull/27');
  expect(screen.getByRole('link', { name: 'Open this run in Runs' })).toHaveAttribute('href', '/app#run=run-1');
  const write = fetch.mock.calls.map(([, init]) => JSON.parse(String(init.body)) as { query: string; variables: Record<string, unknown> })
    .find(request => request.query.includes('createIssueFromConversation'));
  expect(write?.variables).toMatchObject({ conversationId: 'chat-1', title: 'Reviewed: filter runs by status', acceptanceCriteria: ['Selected status hides nonmatching runs.'] });
  expect(screen.getByText('Linked delivery')).toBeVisible();
  expect(screen.getByText(/Verification is complete/)).toBeVisible();
});

it('shows a clear repository setup path when no enabled repository exists', () => {
  const navigate = vi.fn();
  render(<IssueChatPage repositories={[]} operator={operator} onRepositories={navigate} />);
  fireEvent.click(screen.getByRole('button', { name: 'Open repositories' }));
  expect(navigate).toHaveBeenCalledOnce();
});
