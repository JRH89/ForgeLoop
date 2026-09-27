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

it('uses decorative capability icons instead of numbered steps', () => {
  const { container } = render(<LandingPage />);
  const cards = container.querySelectorAll('.landing-grid article');
  expect(cards).toHaveLength(4);
  for (const card of cards) {
    expect(card.querySelector('.capability-icon')).toHaveAttribute('aria-hidden', 'true');
    expect(card.querySelector('.capability-icon svg')).toBeInTheDocument();
    expect(card.querySelector('.capability-icon')?.textContent).toBe('');
    expect(card.querySelector('h3')).not.toBeEmptyDOMElement();
  }
});
