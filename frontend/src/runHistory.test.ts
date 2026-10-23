import { afterEach, expect, it, vi } from 'vitest';
import { loadRuns, loadRunHistory, loadRunForSource, RUN_PAGE_SIZE } from './api';

afterEach(() => vi.unstubAllGlobals());

it('requests bounded server-side pagination and archive filtering', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { featureRuns: [] } }) });
  vi.stubGlobal('fetch', fetch);
  await loadRuns(3, true);
  const body = JSON.parse(fetch.mock.calls[0][1].body);
  expect(body.variables).toEqual({ page: 3, size: RUN_PAGE_SIZE, archived: true });
  expect(body.query).toContain('featureRuns(page:$page,size:$size,archived:$archived)');
});

it('loads all history pages for cost reports and deduplicates moving page boundaries', async () => {
  const first = Array.from({ length: RUN_PAGE_SIZE }, (_, index) => ({ id: `run-${index}` }));
  const fetch = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { featureRuns: first } }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { featureRuns: [first[49], { id: 'older' }] } }) });
  vi.stubGlobal('fetch', fetch);
  const history = await loadRunHistory();
  expect(history).toHaveLength(RUN_PAGE_SIZE + 1);
  expect(history.at(-1)?.id).toBe('older');
  expect(JSON.parse(fetch.mock.calls[1][1].body).variables.page).toBe(1);
});

it('looks up issue delivery directly rather than searching the first history page', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { featureRunForSource: null } }) });
  vi.stubGlobal('fetch', fetch);
  expect(await loadRunForSource('acme/repo', 'issue-10')).toBeNull();
  expect(JSON.parse(fetch.mock.calls[0][1].body).variables).toEqual({ repository: 'acme/repo', sourceRef: 'issue-10' });
});
