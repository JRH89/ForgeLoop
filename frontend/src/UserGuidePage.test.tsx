import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import UserGuidePage from './UserGuidePage';

it('renders the shared runner installation guide and honest enrollment boundary', () => {
  render(<UserGuidePage />);
  expect(screen.queryByRole('navigation', { name: 'User guide sections' })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Install a runner and configure provider keys' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Choose models and set API keys' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Guided CLI installation (advanced fallback)' })).toBeInTheDocument();
  expect(screen.getAllByText(/ANTHROPIC_API_KEY/).length).toBeGreaterThan(0);
});
