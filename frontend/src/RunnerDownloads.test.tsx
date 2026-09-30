import '@testing-library/jest-dom/vitest';
import { render,screen } from '@testing-library/react';
import { afterEach,beforeEach,expect,it,vi } from 'vitest';
import RunnerDownloads,{verifiedManifest} from './RunnerDownloads';
import { legacyReleaseFixture, releaseFixture } from './testFixtures/desktopRelease';
afterEach(()=>vi.unstubAllGlobals());
beforeEach(()=>vi.stubGlobal('matchMedia',vi.fn().mockReturnValue({matches:false,addEventListener:vi.fn()})));
function completeManifestPackages(release=releaseFixture()){
  return release.assets.map((asset:{name:string,browser_download_url:string,digest:string})=>{
    const match=/forgeloop-runner-\d+\.\d+\.\d+-(windows|macos|linux)-(x64|arm64)\.(msi|dmg|deb|rpm|AppImage|tar\.gz|pkg\.tar\.zst)$/.exec(asset.name)!;
    const platform=match[1]==='windows'?'Windows':match[1]==='macos'?'macOS':'Linux';
    return {platform,architecture:match[2],format:match[3].toLowerCase(),filename:asset.name,url:asset.browser_download_url,sha256:asset.digest.slice(7)};
  });
}
it('does not promote unpublished or malformed installers',()=>{
  expect(verifiedManifest({published:false,packages:[]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:[{platform:'Windows',architecture:'x64',url:'javascript:bad',sha256:'a'.repeat(64)}]})).toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:completeManifestPackages()})).not.toBeNull();
  const previous=completeManifestPackages(legacyReleaseFixture());
  expect(verifiedManifest({published:true,version:'1',packages:previous})).not.toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:previous.slice(0,4)})).not.toBeNull();
  expect(verifiedManifest({published:true,version:'1',packages:previous.slice(0,5)})).toBeNull();
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
  expect(screen.getAllByRole('link',{name:/Download for/})).toHaveLength(12);
  expect(screen.getByRole('link',{name:'Download for Linux (x64, RPM)'})).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Download for Linux (x64, Arch package)'})).toBeInTheDocument();
  expect(screen.getByRole('heading',{name:'Linux arm64 · Portable archive'})).toBeInTheDocument();
  expect(screen.getByRole('heading',{name:'Windows (arm64)'})).toBeInTheDocument();
  expect(screen.queryByText(/AppImage-compatible desktops/)).not.toBeInTheDocument();
  expect(screen.getByText(/Native pacman package for Arch Linux, Omarchy, and Manjaro/i)).toBeInTheDocument();
  expect(screen.getByText(/sudo pacman -U.*linux-x64\.pkg\.tar\.zst/)).toBeInTheDocument();
  expect(screen.getByText(/sudo pacman -U.*linux-arm64\.pkg\.tar\.zst/)).toBeInTheDocument();
  expect(screen.getByText(/Saving provider credentials on Linux requires/)).toBeInTheDocument();
  expect(screen.getByText('secret-tool')).toBeInTheDocument();
  expect(screen.getByText(/These preview installers are unsigned/)).toBeInTheDocument();
  expect(screen.getAllByText('Verify download')).toHaveLength(12);
});
