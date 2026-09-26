import { useEffect, useState } from 'react';

type Package = { platform: string; architecture: string; url: string; sha256: string };
type Manifest = { version: string | null; published: boolean; packages: Package[] };
/** Public release links are explicitly published; CI artifacts are never silently promoted. */
export function verifiedManifest(value: unknown): Manifest | null {
  if(!value||typeof value!=='object')return null;
  const manifest=value as Manifest;
  if(manifest.published!==true||typeof manifest.version!=='string'||!Array.isArray(manifest.packages)||manifest.packages.length===0)return null;
  if(manifest.packages.some(item=>!item||!['Windows','macOS','Linux'].includes(item.platform)||!['x64','arm64'].includes(item.architecture)||typeof item.sha256!=='string'||! /^[a-f0-9]{64}$/.test(item.sha256)||typeof item.url!=='string'||!item.url.startsWith('https://github.com/JRH89/ForgeLoop/releases/download/')))return null;
  return manifest;
}
export default function RunnerDownloads(){
  const [manifest,setManifest]=useState<Manifest|null>(null);
  const [loading,setLoading]=useState(true);
  useEffect(()=>{const controller=new AbortController();void fetch('/downloads/desktop-manifest.json',{signal:controller.signal,cache:'no-store'}).then(response=>{if(!response.ok)throw new Error('Unavailable');return response.json();}).then(value=>setManifest(verifiedManifest(value))).catch(()=>setManifest(null)).finally(()=>setLoading(false));return()=>controller.abort();},[]);
  return <main className="guide panel guide-section"><h1>Install ForgeLoop Runner</h1>
    <p>Run agents on a computer you control. The desktop installer includes Java; Git and Docker are checked during setup.</p>
    {loading?<p role="status">Checking available installers…</p>:manifest?<><h2>Version {manifest.version}</h2>{manifest.packages.map(item=><div className="item" key={`${item.platform}-${item.architecture}`}><a href={item.url} rel="noopener noreferrer">Download for {item.platform} ({item.architecture})</a><small>SHA-256: <code style={{overflowWrap:'anywhere'}}>{item.sha256}</code></small></div>)}</>:<p role="status">Desktop installers are in development verification. Signed public downloads are not available yet; the existing CLI package remains available below.</p>}
    <ol><li>Install and open ForgeLoop Runner.</li><li>Connect in your browser, sign in with GitHub, and approve the matching fingerprint.</li><li>Choose your provider/model and save your API key locally.</li><li>Check Git and Docker, then explicitly start work. Eligible issues can incur API charges.</li></ol>
    <h2>Updates</h2><p>Pause and wait for active work to finish before closing the app. Install the newer package, then reopen it. Your private runner identity and settings are kept outside the application directory. Never bypass an invalid publisher signature or checksum mismatch.</p>
    <p><a href="/downloads/forgeloop-runner.zip">Advanced CLI package</a> · <a href="/downloads/forgeloop-runner.zip.sha256">CLI checksum</a> · <a href="/app">Return to dashboard</a></p>
  </main>;
}
