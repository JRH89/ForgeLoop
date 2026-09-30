export function releaseFixture(version='1.0.4', preview=true) {
  const tag=`desktop-v${version}${preview?'-preview.1':''}`;
  return { draft:false,prerelease:preview,tag_name:tag,published_at:'2026-09-27T12:00:00Z',assets:
    ['windows-x64.msi','windows-arm64.msi','macos-arm64.dmg','macos-x64.dmg',
      'linux-x64.deb','linux-x64.rpm','linux-x64.tar.gz','linux-x64.pkg.tar.zst',
      'linux-arm64.deb','linux-arm64.rpm','linux-arm64.tar.gz','linux-arm64.pkg.tar.zst'].map(target=>({
      name:`forgeloop-runner-${version}-${target}`,state:'uploaded',size:1024,digest:`sha256:${'a'.repeat(64)}`,
      browser_download_url:`https://github.com/JRH89/ForgeLoop/releases/download/${tag}/forgeloop-runner-${version}-${target}`
    })) };
}

export function legacyReleaseFixture(version='1.0.4',preview=true) {
  const tag=`desktop-v${version}${preview?'-preview.1':''}`;
  return { draft:false,prerelease:preview,tag_name:tag,published_at:'2026-09-27T12:00:00Z',assets:
    ['windows-x64.msi','macos-arm64.dmg','macos-x64.dmg','linux-x64.deb','linux-x64.rpm','linux-x64.AppImage'].map(target=>({
      name:`forgeloop-runner-${version}-${target}`,state:'uploaded',size:1024,digest:`sha256:${'a'.repeat(64)}`,
      browser_download_url:`https://github.com/JRH89/ForgeLoop/releases/download/${tag}/forgeloop-runner-${version}-${target}`
    })) };
}
