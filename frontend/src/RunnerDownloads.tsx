import { useEffect, useState } from 'react';
import { latestDesktopRelease, verifiedManifest, type DesktopRelease } from './desktopReleases';
import { PublicLayout } from './public/PublicLayout';
import { initializeNavigation } from './public/navigation';
export { verifiedManifest } from './desktopReleases';

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
      <div className="public-cards">{manifest.packages.map(item=><article key={item.platform+'-'+item.architecture}>
        <h3>{item.platform} ({item.architecture==='arm64'?'Apple Silicon':item.architecture==='x64'&&item.platform==='macOS'?'Intel':'x64'})</h3>
        {item.platform==='Linux'&&<p>DEB installer for Debian/Ubuntu desktops.</p>}
        <a className="primary" href={item.url}>Download for {item.platform} ({item.architecture})</a>
        <details className="download-verification"><summary>Verify download</summary>
          <p>SHA-256</p><code style={{overflowWrap:'anywhere'}}>{item.sha256}</code>
          <button type="button" onClick={()=>void copyChecksum(item.sha256)}>{copied===item.sha256?'Copied':'Copy SHA-256'}</button>
          <p>After downloading, run:</p>
          <code style={{overflowWrap:'anywhere'}}>{item.platform==='Windows'?'Get-FileHash .\\'+(item.filename??'installer.msi')+' -Algorithm SHA256':(item.platform==='macOS'?'shasum -a 256 ':'sha256sum ')+(item.filename??'installer')}</code>
          <p>Compare the complete value with the checksum above. If they differ, do not install that file. Checksums confirm file integrity; publisher signing is separate.</p>
        </details>
      </article>)}</div>
      <p role="status" aria-live="polite">{copied==='failed'?'Could not copy automatically. Select and copy the checksum above.':copied?'Checksum copied.':''}</p>
    </>:<p role="status">Desktop downloads are temporarily unavailable here. <a href="https://github.com/JRH89/ForgeLoop/releases">Check GitHub Releases</a> or use the CLI package below.</p>}
    <h2>Get connected</h2>
    <ol><li>Install and open ForgeLoop Runner.</li><li>Connect in your browser, sign in with GitHub, and approve the matching fingerprint.</li><li>Choose your provider/model and save your API key locally.</li><li>Check Git and Docker, then explicitly start work. Eligible issues can incur API charges.</li></ol>
    <h2>Updates</h2><p>This page checks the latest published desktop release automatically. Pause and wait for active work to finish before closing the app. Install the newer package, then reopen it. Your private runner identity and settings are kept outside the application directory.</p>
    <p><a href="/downloads/forgeloop-runner.zip">Advanced CLI package</a> · <a href="/downloads/forgeloop-runner.zip.sha256">CLI checksum</a> · <a href="/app">Open dashboard</a></p>
  </section></PublicLayout>;
}
