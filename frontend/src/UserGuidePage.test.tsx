import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import UserGuidePage from './UserGuidePage';

it('renders the shared runner installation guide and honest enrollment boundary', () => {
  render(<UserGuidePage />);
  expect(screen.getByRole('link', { name: 'Runner setup & API keys' })).toHaveAttribute('href', '#runner-setup');
  expect(screen.getByRole('heading', { name: 'Install a runner and configure provider keys' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Choose models and set API keys' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Guided installation (recommended)' })).toBeInTheDocument();
  expect(screen.getAllByText(/ANTHROPIC_API_KEY/).length).toBeGreaterThan(0);
});
