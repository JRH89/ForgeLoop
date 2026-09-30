import { expect, it } from 'vitest';
import { latestDesktopRelease } from './desktopReleases';
import { legacyReleaseFixture, releaseFixture } from './testFixtures/desktopRelease';

it('selects the highest complete desktop version and binds URLs and hashes to it',()=>{
  const result=latestDesktopRelease([releaseFixture('1.0.4'),releaseFixture('1.0.10')]);
  expect(result?.version).toBe('1.0.10');
  expect(result?.preview).toBe(true);
  expect(result?.packages).toHaveLength(12);
  expect(result?.packages.filter(item=>item.platform==='Linux').map(item=>item.format)).toEqual(['deb','rpm','tar.gz','pkg.tar.zst','deb','rpm','tar.gz','pkg.tar.zst']);
  expect(result?.packages.some(item=>item.platform==='Windows'&&item.architecture==='arm64')).toBe(true);
  expect(result?.packages.every(item=>item.url.includes('desktop-v1.0.10-preview.1')&&item.sha256==='a'.repeat(64))).toBe(true);
});
it('ignores drafts, incomplete uploads and unrelated releases',()=>{
  const draft={...releaseFixture('2.0.0'),draft:true};
  const incomplete=releaseFixture('3.0.0');incomplete.assets.pop();
  expect(latestDesktopRelease([draft,incomplete,{tag_name:'v9.0.0'},releaseFixture()])?.version).toBe('1.0.4');
  expect(latestDesktopRelease({message:'rate limited'})).toBeNull();
});
it('keeps prior DEB-only and AppImage releases usable without recommending AppImage',()=>{
  const legacy=legacyReleaseFixture('1.0.5');
  legacy.assets=legacy.assets.filter(asset=>/windows-x64\.msi|macos-(arm64|x64)\.dmg|linux-x64\.deb$/.test(asset.name));
  const selected=latestDesktopRelease([legacy]);
  expect(selected?.packages).toHaveLength(4);
  expect(selected?.packages.some(item=>item.platform==='Linux'&&item.format==='deb')).toBe(true);
  const older=latestDesktopRelease([legacyReleaseFixture('1.0.4')]);
  expect(older?.packages).toHaveLength(5);
  expect(older?.packages.some(item=>item.format==='appimage')).toBe(false);
});
it('rejects missing digests, substituted URLs, duplicate targets and mislabeled previews',()=>{
  for(const mutation of ['digest','url','duplicate','preview']){
    const release=releaseFixture();
    if(mutation==='digest')release.assets[0].digest='';
    if(mutation==='url')release.assets[0].browser_download_url='https://example.com/installer.msi';
    if(mutation==='duplicate')release.assets[0]=release.assets[1];
    if(mutation==='preview')release.prerelease=false;
    expect(latestDesktopRelease([release])).toBeNull();
  }
});
it('prefers stable over preview at the same version',()=>{
  expect(latestDesktopRelease([releaseFixture(),releaseFixture('1.0.4',false)])?.preview).toBe(false);
});
