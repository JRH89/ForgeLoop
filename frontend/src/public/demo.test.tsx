import '@testing-library/jest-dom/vitest';
import { render, screen, within } from '@testing-library/react';
import { expect, it } from 'vitest';
import PublicSite from './PublicSite';
import { DEMO_RUN, demoStages } from './demo';
import { metadata, publicPaths } from './seo';

it('replays the recorded unattended run with links to its public evidence', () => {
  render(<PublicSite path="/demo"/>);
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Watch an issue become a merged PR.');
  const timeline = document.querySelector('.demo-timeline') as HTMLElement;
  expect(within(timeline).getAllByRole('listitem')).toHaveLength(demoStages.length);
  expect(screen.getByRole('link', { name: 'project-hub #10' })).toHaveAttribute('href', 'https://github.com/JRH89/project-hub/issues/10');
  expect(screen.getByRole('link', { name: 'Open PR #11' })).toHaveAttribute('href', 'https://github.com/JRH89/project-hub/pull/11');
  expect(screen.getByRole('link', { name: 'View merge commit' })).toHaveAttribute('href', expect.stringContaining(DEMO_RUN.mergeCommit));
  expect(screen.getByRole('group', { name: 'Recorded verification gate' })).toHaveTextContent('October 9, 2026 at 7:09 PM UTC');
  // Limits stay visible so the demo never overstates the evidence.
  expect(screen.getByRole('heading', { name: 'What this demo does not claim' })).toBeInTheDocument();
});

it('is discoverable from navigation and has its own search metadata', () => {
  render(<PublicSite path="/features"/>);
  expect(screen.getByRole('navigation', { name: 'Main navigation' })).toContainElement(screen.getAllByRole('link', { name: 'Demo' })[0]);
  expect(publicPaths).toContain('/demo');
  expect(metadata('/demo').hero.image).toBe('/images/demo-hero.webp');
});
