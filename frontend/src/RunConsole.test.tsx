import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './main';

afterEach(() => vi.unstubAllGlobals());

it('shows only persisted generic repository controls', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { featureRuns: [], repositoryConnections: [] } }) }));
  render(<App />);
  expect(await screen.findByText('Start a delivery run')).toBeInTheDocument();
  expect(screen.getByLabelText('Authorized repository')).toBeInTheDocument();
  expect(screen.queryByText('Support Desk')).not.toBeInTheDocument();
});
