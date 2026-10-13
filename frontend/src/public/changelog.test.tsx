import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import PublicSite from './PublicSite';
import { metadata, publicPaths } from './seo';

it('publishes a discoverable milestone changelog with release references and SEO', () => {
  render(<PublicSite path="/changelog"/>);
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('What changed in ForgeLoop.');
  expect(screen.getByRole('link', { name: /Runner 1.0.11 release/ })).toHaveAttribute('href', 'https://github.com/JRH89/ForgeLoop/releases/tag/desktop-v1.0.11-preview.1');
  expect(screen.getByRole('link', { name: 'Changelog' })).toHaveAttribute('href', '/changelog');
  expect(publicPaths).toContain('/changelog');
  expect(metadata('/changelog').title).toContain('changelog');
});
