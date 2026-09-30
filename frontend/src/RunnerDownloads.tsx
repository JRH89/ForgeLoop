import { useEffect, useState } from 'react';
import { latestDesktopRelease, verifiedManifest, type DesktopRelease } from './desktopReleases';
import { PublicLayout } from './public/PublicLayout';
import { initializeNavigation } from './public/navigation';
export { verifiedManifest } from './desktopReleases';

function packageTitle(item: DesktopRelease['packages'][number]): string {
  if (item.platform === 'Linux') {
    const format=item.format==='tar.gz'?'Portable archive':item.format==='pkg.tar.zst'?'Arch package':item.format.toUpperCase();
    return `Linux ${item.architecture} · ${format}`;
  }
  if (item.platform === 'Windows') return `Windows (${item.architecture})`;
  return item.architecture === 'arm64' ? 'macOS (Apple Silicon)' : 'macOS (Intel)';
}

function packageDescription(item: DesktopRelease['packages'][number]): string {
  if (item.platform === 'Linux') {
    if (item.format === 'deb') return item.architecture==='arm64'?'DEB package for Debian and Ubuntu on ARM.':'DEB package for Debian, Ubuntu, and Linux Mint.';
    if (item.format === 'rpm') return item.architecture==='arm64'?'RPM package for ARM64 Fedora and compatible systems.':'RPM package for Fedora, RHEL, and openSUSE.';
    if (item.format === 'tar.gz') return `Portable ${item.architecture} Linux archive with Java bundled; extract it and run ForgeLoop Runner.`;
    if (item.format === 'pkg.tar.zst') return item.architecture==='arm64'?'Arch Linux ARM pacman package.':'Native pacman package for Arch Linux, Omarchy, and Manjaro.';
    return 'Legacy AppImage package.';
  }
  if (item.platform === 'Windows') return `MSI installer for Windows ${item.architecture}.`;
  return item.architecture === 'arm64' ? 'DMG installer for Apple Silicon Macs.' : 'DMG installer for Intel Macs.';
}

function downloadLabel(item: DesktopRelease['packages'][number]): string {
  if (item.platform === 'Linux') {
    const format=item.format==='tar.gz'?'portable archive':item.format==='pkg.tar.zst'?'Arch package':item.format.toUpperCase();
    return `Download for Linux (${item.architecture}, ${format})`;
  }
  return `Download for ${item.platform} (${item.architecture})`;
}

export default function RunnerDownloads(){
  const [manifest,setManifest]=useState<DesktopRelease|null>(null);
  const [loading,setLoading]=useState(true);
  const [copied,setCopied]=useState('');
  useEffect(initializeNavigation,[]);
  useEffect(()=>{
    const controller=new AbortController();
    async function load(){
      let release: DesktopRelease|null=null;
      try {
        const response=await fetch('/downloads/desktop-releases.json',{signal:controller.signal});
        if(response.ok) release=latestDesktopRelease(await response.json());
      } catch { /* A local manifest can retain an explicitly published fallback. */ }
      if(!release&&!controller.signal.aborted){
        try {
          const response=await fetch('/downloads/desktop-manifest.json',{signal:controller.signal,cache:'no-store'});
          if(response.ok) release=verifiedManifest(await response.json());
        } catch { /* The release-page link remains usable during a feed outage. */ }
      }
      if(!controller.signal.aborted){setManifest(release);setLoading(false);}
    }
    void load();
    return()=>controller.abort();
  },[]);
  async function copyChecksum(value:string){
    try {await navigator.clipboard.writeText(value);setCopied(value);}
    catch {setCopied('failed');}
  }
  return <PublicLayout><section className="public-section runner-downloads"><h1>Install ForgeLoop Runner</h1>
    <p>Run agents on a computer you control. Java is included; install Git and Docker before starting work.</p>
    {loading?<p role="status">Checking available installers…</p>:manifest?<>
      <h2>Version {manifest.version}{manifest.preview?' — Preview':''}</h2>
      {manifest.preview&&<p className="guide-callout">These preview installers are unsigned. Windows may show an unknown-publisher warning, and macOS may block the app because it is not notarized. Use a preview only if your device policy allows it.</p>}
      {manifest.releaseUrl&&<p><a href={manifest.releaseUrl}>Release notes and all downloads</a></p>}
      <div className="public-cards">{manifest.packages.filter(item=>item.format!=='appimage').map(item=><article key={`${item.platform}-${item.architecture}-${item.format}`}>
        <h3>{packageTitle(item)}</h3>
        <p>{packageDescription(item)}</p>
        <a className="primary" href={item.url}>{downloadLabel(item)}</a>
        <details className="download-verification"><summary>Verify download</summary>
          <p>SHA-256</p><code style={{overflowWrap:'anywhere'}}>{item.sha256}</code>
          <button type="button" onClick={()=>void copyChecksum(item.sha256)}>{copied===item.sha256?'Copied':'Copy SHA-256'}</button>
          <p>After downloading, run:</p>
          <code style={{overflowWrap:'anywhere'}}>{item.platform==='Windows'?'Get-FileHash .\\'+(item.filename??'installer.msi')+' -Algorithm SHA256':(item.platform==='macOS'?'shasum -a 256 ':'sha256sum ')+(item.filename??'installer')}</code>
          <p>Compare the complete value with the checksum above. If they differ, do not install that file. Checksums confirm file integrity; publisher signing is separate.</p>
        </details>
      </article>)}</div>
      {manifest.packages.some(item=>item.platform==='Linux')&&<details className="download-verification linux-install-notes">
        <summary>Linux install notes</summary>
        {manifest.packages.filter(item=>item.platform==='Linux'&&item.format==='pkg.tar.zst').map(item=><p key={item.architecture}>For Arch Linux {item.architecture} (including Omarchy on x64), install with <code>sudo pacman -U ./forgeloop-runner-{manifest.version}-linux-{item.architecture}.{item.format}</code>. This is a native pacman package, not an AppImage.</p>)}
        {manifest.packages.some(item=>item.platform==='Linux'&&item.format==='tar.gz')&&<p>The portable <code>.tar.gz</code> archive works across distributions: extract it and run <code>./run-forgeloop-runner.sh</code>. It does not require AppImage or FUSE.</p>}
        <p>Choose DEB for Debian, Ubuntu, or Mint; RPM for Fedora, RHEL, or openSUSE. ARM64 builds are available for supported package formats.</p>
        <p>Saving provider credentials on Linux requires <code>secret-tool</code> and an unlocked desktop Secret Service keyring. Install the libsecret command-line tools for your distribution.</p>
      </details>}
      <p role="status" aria-live="polite">{copied==='failed'?'Could not copy automatically. Select and copy the checksum above.':copied?'Checksum copied.':''}</p>
    </>:<p role="status">Desktop downloads are temporarily unavailable here. <a href="https://github.com/JRH89/ForgeLoop/releases">Check GitHub Releases</a> or use the CLI package below.</p>}
    <h2>Get connected</h2>
    <ol><li>Install and open ForgeLoop Runner.</li><li>Connect in your browser, sign in with GitHub, and approve the matching fingerprint.</li><li>Choose your provider/model and save your API key locally.</li><li>Check Git and Docker, then explicitly start work. Eligible issues can incur API charges.</li></ol>
    <h2>Updates</h2><p>This page checks the latest published desktop release automatically. Pause and wait for active work to finish before closing the app. Install the newer package, then reopen it. Your private runner identity and settings are kept outside the application directory.</p>
    <p><a href="/downloads/forgeloop-runner.zip">Advanced CLI package</a> · <a href="/downloads/forgeloop-runner.zip.sha256">CLI checksum</a> · <a href="/app">Open dashboard</a></p>
  </section></PublicLayout>;
}
