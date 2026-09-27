import '@testing-library/jest-dom/vitest';
import { render,screen } from '@testing-library/react';
import { afterEach,beforeEach,expect,it,vi } from 'vitest';
import RunnerDownloads,{verifiedManifest} from './RunnerDownloads';
import { releaseFixture } from './testFixtures/desktopRelease';
afterEach(()=>vi.unstubAllGlobals());
beforeEach(()=>vi.stubGlobal('matchMedia',vi.fn().mockReturnValue({matches:false,addEventListener:vi.fn()})));
it('does not promote unpublished or malformed installers',()=>{
  expect(verifiedManifest({published:false,packages:[]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:[{platform:'Windows',architecture:'x64',url:'javascript:bad',sha256:'a'.repeat(64)}]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:[{platform:'Windows',architecture:'x64',url:'https://github.com/JRH89/ForgeLoop/releases/download/v1/runner.msi',sha256:'a'.repeat(64)}]})).not.toBeNull();
});
it('explains the release boundary and preserves the CLI fallback',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>({published:false,packages:[]})}));
  render(<RunnerDownloads/>);
  expect(await screen.findByText(/Desktop downloads are temporarily unavailable here/)).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Advanced CLI package'})).toHaveAttribute('href','/downloads/forgeloop-runner.zip');
});

it('shows a complete preview with optional checksum verification',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>[releaseFixture()]}));
  render(<RunnerDownloads/>);
  expect(await screen.findByRole('heading',{name:'Version 1.0.4 — Preview'})).toBeInTheDocument();
  expect(screen.getAllByRole('link',{name:/Download for/})).toHaveLength(4);
  expect(screen.getByText(/These preview installers are unsigned/)).toBeInTheDocument();
  expect(screen.getAllByText('Verify download')).toHaveLength(4);
});
