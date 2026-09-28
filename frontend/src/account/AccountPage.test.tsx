import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import type { OrganizationMember, OperatorSession } from '../api';
import AccountPage from './AccountPage';

vi.mock('../support/SupportApp', () => ({ default: () => <div>Embedded ticket management</div> }));

const operator: OperatorSession = { subject: 'github-user-17', organizationId: 'org-1', role: 'ADMIN' };
const workspaces: OrganizationMember[] = [{ id: 'membership-1', subject: operator.subject, organizationId: 'org-1', organizationName: 'Example team', role: 'ADMIN', accepted: true }];

it('summarizes account and workspace access and embeds the ticket center', () => {
  render(<AccountPage operator={operator} workspaces={workspaces} onUsage={vi.fn()} onWorkspaceSettings={vi.fn()} />);

  expect(screen.getByRole('heading', { name: 'Account' })).toBeInTheDocument();
  expect(screen.getByText('github-user-17')).toBeInTheDocument();
  expect(screen.getByText('Example team')).toBeInTheDocument();
  expect(screen.getByText('Embedded ticket management')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'New support request' })).toHaveAttribute('href', '/contact#support');
  expect(screen.getByRole('link', { name: /Runner & security guide/ })).toHaveAttribute('href', '/docs');
});

it('routes account shortcuts to usage and workspace configuration', () => {
  const onUsage = vi.fn(), onWorkspaceSettings = vi.fn();
  render(<AccountPage operator={operator} workspaces={workspaces} onUsage={onUsage} onWorkspaceSettings={onWorkspaceSettings} />);
  fireEvent.click(screen.getByRole('button', { name: /Usage & costs/ }));
  fireEvent.click(screen.getByRole('button', { name: /Workspace settings/ }));
  expect(onUsage).toHaveBeenCalledOnce();
  expect(onWorkspaceSettings).toHaveBeenCalledOnce();
});
