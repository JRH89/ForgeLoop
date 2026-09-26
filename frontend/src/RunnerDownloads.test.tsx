import '@testing-library/jest-dom/vitest';
import { render,screen } from '@testing-library/react';
import { afterEach,expect,it,vi } from 'vitest';
import RunnerDownloads,{verifiedManifest} from './RunnerDownloads';
afterEach(()=>vi.unstubAllGlobals());
it('does not promote unpublished or malformed installers',()=>{
  expect(verifiedManifest({published:false,packages:[]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:[{platform:'Windows',architecture:'x64',url:'javascript:bad',sha256:'a'.repeat(64)}]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:[{platform:'Windows',architecture:'x64',url:'https://github.com/JRH89/ForgeLoop/releases/download/v1/runner.msi',sha256:'a'.repeat(64)}]})).not.toBeNull();
});
it('explains the release boundary and preserves the CLI fallback',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>({published:false,packages:[]})}));
  render(<RunnerDownloads/>);
  expect(await screen.findByText(/Signed public downloads are not available yet/)).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Advanced CLI package'})).toHaveAttribute('href','/downloads/forgeloop-runner.zip');
});
