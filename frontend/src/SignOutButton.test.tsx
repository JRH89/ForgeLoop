import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { SignOutButton } from './SignOutButton';

it('signs out with a same-origin POST form instead of a GET link', () => {
  render(<SignOutButton className="desktop-signout"/>);
  const button = screen.getByRole('button', { name: 'Sign out' });
  expect(button).toHaveAttribute('type', 'submit');
  expect(button).toHaveClass('logout', 'desktop-signout');
  const form = button.closest('form');
  expect(form).toHaveAttribute('method', 'post');
  expect(form).toHaveAttribute('action', '/logout');
  expect(screen.queryByRole('link', { name: 'Sign out' })).toBeNull();
});
