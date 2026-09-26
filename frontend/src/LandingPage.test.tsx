import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import LandingPage from './LandingPage';

it('offers GitHub authentication without loading tenant data', () => {
  render(<LandingPage />);
  expect(screen.getByRole('heading', { name: /Turn GitHub issues into/ })).toBeInTheDocument();
  const signIn = screen.getAllByRole('link', { name: /Sign in with GitHub|Open the console/ });
  expect(signIn.every(link => link.getAttribute('href') === '/oauth2/authorization/github')).toBe(true);
});
