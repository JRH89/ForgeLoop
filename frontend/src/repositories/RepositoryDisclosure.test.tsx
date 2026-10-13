import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RepositoryDisclosure from './RepositoryDisclosure';

afterEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

it('opens by default and restores the last choice independently for each repository', async () => {
  const view = render(<RepositoryDisclosure storageKey="repo-a" heading="Repository A">Contents</RepositoryDisclosure>);
  const details = screen.getByText('Repository A').closest('details')!;
  expect(details).toHaveAttribute('open');
  details.open = false;
  fireEvent(details, new Event('toggle'));
  await waitFor(() => expect(localStorage.getItem('repo-a')).toBe('closed'));
  view.unmount();
  const restored = render(<RepositoryDisclosure storageKey="repo-a" heading="Repository A">Contents</RepositoryDisclosure>);
  expect(screen.getByText('Repository A').closest('details')).not.toHaveAttribute('open');
  restored.rerender(<RepositoryDisclosure key="repo-b" storageKey="repo-b" heading="Repository B">Other contents</RepositoryDisclosure>);
  expect(screen.getByText('Repository B').closest('details')).toHaveAttribute('open');
});

it('remains usable when storage is blocked', () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
  render(<RepositoryDisclosure storageKey="repo" heading="Repository">Contents</RepositoryDisclosure>);
  const details = screen.getByText('Repository').closest('details')!;
  details.open = false;
  fireEvent(details, new Event('toggle'));
  expect(details).not.toHaveAttribute('open');
});
