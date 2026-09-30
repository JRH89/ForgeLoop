import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RepositoryBudgetSettings from './RepositoryBudgetSettings';
import type { RepositoryConnection } from './api';

afterEach(() => vi.unstubAllGlobals());

const item = { id: 'repo-id', repository: 'JRH89/Ticketly', maxBudgetUsd: 25 } as RepositoryConnection;

it('saves a repository ceiling and reports the persisted policy', async () => {
  const saved = { ...item, maxBudgetUsd: 5, policyRevision: 4 };
  const fetch = vi.fn(async (url: string, init: RequestInit) => {
    expect(url).toBe('/graphql');
    expect(init.method).toBe('POST');
    return { ok: true, json: async () => ({ data: { configureRepositoryBudget: saved } }) };
  });
  const onSaved = vi.fn();
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryBudgetSettings item={item} organizationLimit={5} editable onSaved={onSaved}/>);

  fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum run budget for JRH89/Ticketly' }), { target: { value: '5' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save repository budget' }));

  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body)).variables)
    .toEqual({ repository: 'JRH89/Ticketly', maxBudgetUsd: 5 });
});

it('keeps budget controls unavailable to non-admin members', () => {
  render(<RepositoryBudgetSettings item={item} organizationLimit={5} editable={false} onSaved={() => {}}/>);

  expect(screen.getByRole('spinbutton', { name: 'Maximum run budget for JRH89/Ticketly' })).toBeDisabled();
  expect(screen.queryByRole('button', { name: 'Save repository budget' })).not.toBeInTheDocument();
});

it('rejects values above the organization limit before making a request', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  render(<RepositoryBudgetSettings item={item} organizationLimit={5} editable onSaved={() => {}}/>);

  fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum run budget for JRH89/Ticketly' }), { target: { value: '6' } });
  fireEvent.submit(document.querySelector('.repository-budget-settings')!);

  expect(await screen.findByRole('alert')).toHaveTextContent('no more than the organization limit of $5');
  expect(fetch).not.toHaveBeenCalled();
});
