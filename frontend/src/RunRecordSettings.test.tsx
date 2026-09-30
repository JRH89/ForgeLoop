import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RunRecordSettings from './RunRecordSettings';
import type { RepositoryConnection } from './api';

afterEach(() => vi.unstubAllGlobals());

const item = { id: 'repo-id', repository: 'org/repo', runRecord: false } as RepositoryConnection;

it('requires an explicit admin save and reports the saved run-record opt-in', async () => {
  const saved = { ...item, runRecord: true };
  const fetch = vi.fn(async (url: string, init: RequestInit) => {
    expect(url).toBe('/graphql');
    expect(init.method).toBe('POST');
    return { ok: true, json: async () => ({ data: { configureRepositoryRunRecord: saved } }) };
  });
  const onSaved = vi.fn();
  vi.stubGlobal('fetch', fetch);
  render(<RunRecordSettings item={item} editable onSaved={onSaved}/>);

  fireEvent.click(screen.getByRole('checkbox', { name: 'Save run records for org/repo' }));
  fireEvent.click(screen.getByRole('button', { name: 'Save record setting' }));

  expect(await screen.findByRole('checkbox', { name: 'Save run records for org/repo' })).toBeChecked();
  expect(onSaved).toHaveBeenCalledWith(saved);
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body)).variables)
    .toEqual({ repository: 'org/repo', enabled: true });
});

it('keeps the control read-only for non-admin members', () => {
  render(<RunRecordSettings item={item} editable={false} onSaved={() => {}}/>);

  expect(screen.getByRole('checkbox', { name: 'Save run records for org/repo' })).toBeDisabled();
  expect(screen.queryByRole('button', { name: 'Save record setting' })).not.toBeInTheDocument();
});
