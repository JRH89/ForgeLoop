import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import SetupBanner from './SetupBanner';
import type { OperatorSession } from './api';

const operator: OperatorSession = { subject: 'user', organizationId: 'workspace', role: 'ADMIN' };
afterEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

it('keeps dismissal after remount but scopes it to the account and workspace', () => {
  const first = render(<SetupBanner operator={operator} onOpen={() => {}}/>);
  fireEvent.click(screen.getByRole('button', { name: 'Dismiss getting started banner permanently' }));
  expect(screen.queryByText('Open setup checklist')).not.toBeInTheDocument();
  first.unmount();
  const second = render(<SetupBanner operator={operator} onOpen={() => {}}/>);
  expect(screen.queryByText('Open setup checklist')).not.toBeInTheDocument();
  second.rerender(<SetupBanner key="other" operator={{ ...operator, organizationId: 'other' }} onOpen={() => {}}/>);
  expect(screen.getByText('Open setup checklist')).toBeInTheDocument();
});

it('opens the checklist and dismisses safely when browser storage is blocked', () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
  const open = vi.fn();
  render(<SetupBanner operator={operator} onOpen={open}/>);
  fireEvent.click(screen.getByText('Open setup checklist'));
  expect(open).toHaveBeenCalledOnce();
  fireEvent.click(screen.getByRole('button', { name: 'Dismiss getting started banner permanently' }));
  expect(screen.queryByText('Open setup checklist')).not.toBeInTheDocument();
});
