export type PackageFormat = 'msi' | 'dmg' | 'deb' | 'rpm' | 'appimage' | 'tar.gz' | 'pkg.tar.zst';
export type DesktopPackage = { platform: string; architecture: string; format: PackageFormat; url: string; sha256: string; filename?: string };
export type DesktopRelease = { version: string; published: true; packages: DesktopPackage[]; preview?: boolean; releaseUrl?: string };
const repository = 'https://github.com/JRH89/ForgeLoop';
const platforms: Record<string, string> = { windows: 'Windows', macos: 'macOS', linux: 'Linux' };
// Accept both prior published target sets during migration; only the expanded
// matrix is considered complete for new desktop releases.
const supportedCompleteTargetSets = [
  ['Windows/x64/msi','macOS/arm64/dmg','macOS/x64/dmg','Linux/x64/deb'],
  ['Windows/x64/msi','macOS/arm64/dmg','macOS/x64/dmg','Linux/x64/deb','Linux/x64/rpm','Linux/x64/appimage'],
  ['Windows/x64/msi','Windows/arm64/msi','macOS/arm64/dmg','macOS/x64/dmg',
    'Linux/x64/deb','Linux/x64/rpm','Linux/x64/tar.gz','Linux/x64/pkg.tar.zst',
    'Linux/arm64/deb','Linux/arm64/rpm','Linux/arm64/tar.gz','Linux/arm64/pkg.tar.zst'],
];

function packageFormat(item: Partial<DesktopPackage>): PackageFormat | null {
  const suppliedFormat=typeof item.format==='string'?item.format.toLowerCase():undefined;
  if (suppliedFormat && ['msi','dmg','deb','rpm','appimage','tar.gz','pkg.tar.zst'].includes(suppliedFormat)) return suppliedFormat as PackageFormat;
  const source = item.filename ?? item.url ?? '';
  const match = /\.(msi|dmg|deb|rpm|appimage|tar\.gz|pkg\.tar\.zst)$/i.exec(source);
  return match ? match[1].toLowerCase() as PackageFormat : null;
}

function packageMatchesPlatform(platform: string, architecture: string, format: PackageFormat): boolean {
  if (platform === 'Windows') return ['x64','arm64'].includes(architecture) && format === 'msi';
  if (platform === 'macOS') return ['x64','arm64'].includes(architecture) && format === 'dmg';
  if (platform === 'Linux') return ['x64','arm64'].includes(architecture)
    && (['deb','rpm','tar.gz','pkg.tar.zst'].includes(format) || architecture === 'x64' && format === 'appimage');
  return false;
}

function hasCompleteTargetSet(packages: DesktopPackage[]): boolean {
  const targets = new Set(packages.map(item => `${item.platform}/${item.architecture}/${item.format}`));
  return supportedCompleteTargetSets.some(required => packages.length === required.length
    && targets.size === required.length && required.every(target => targets.has(target)));
}

/** Only publish asset links belonging to this repository; no arbitrary download hosts. */
function releaseAssetUrl(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  try {
    const url = new URL(value);
    return url.origin === 'https://github.com' && !url.username && !url.password && !url.search && !url.hash
      && url.pathname.startsWith('/JRH89/ForgeLoop/releases/download/');
  } catch { return false; }
}

export function verifiedManifest(value: unknown): DesktopRelease | null {
  if (!value || typeof value !== 'object') return null;
  const manifest = value as DesktopRelease;
  if (manifest.published !== true || typeof manifest.version !== 'string' || !manifest.version || !Array.isArray(manifest.packages) || !manifest.packages.length) return null;
  const packages: DesktopPackage[] = [];
  for (const item of manifest.packages) {
    if (!item || !Object.values(platforms).includes(item.platform) || !['x64','arm64'].includes(item.architecture)
      || typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sha256) || !releaseAssetUrl(item.url)) return null;
    const format = packageFormat(item);
    if (!format || !packageMatchesPlatform(item.platform, item.architecture, format)) return null;
    packages.push({ ...item, format });
  }
  if (!hasCompleteTargetSet(packages)) return null;
  // Legacy local manifests are treated as previews unless explicitly marked otherwise.
  return { version: manifest.version, published: true, packages: packages.filter(item=>item.format!=='appimage'), preview: manifest.preview !== false };
}

/** GitHub provides each asset digest alongside its versioned URL in one response. */
export function latestDesktopRelease(value: unknown): DesktopRelease | null {
  if (!Array.isArray(value)) return null;
  const candidates: {release: DesktopRelease; version: number[]}[] = [];
  for (const entry of value) {
    if (!entry || typeof entry !== 'object' || entry.draft !== false || typeof entry.prerelease !== 'boolean' || typeof entry.tag_name !== 'string' || !entry.published_at || !Array.isArray(entry.assets)) continue;
    const tag = /^desktop-v(\d+\.\d+\.\d+)(?:-preview\.(\d+))?$/.exec(entry.tag_name);
    if (!tag || Boolean(tag[2]) !== entry.prerelease) continue;
    const packages: DesktopPackage[] = [];
    for (const asset of entry.assets) {
      if (!asset || typeof asset.name !== 'string' || asset.state !== 'uploaded' || !(asset.size > 0)) continue;
      const name = /^forgeloop-runner-(\d+\.\d+\.\d+)-(windows|macos|linux)-(x64|arm64)\.(msi|dmg|deb|rpm|appimage|tar\.gz|pkg\.tar\.zst)$/i.exec(asset.name);
      if (!name || name[1] !== tag[1]) continue;
      const format = name[4].toLowerCase() as PackageFormat;
      const platform = platforms[name[2].toLowerCase()];
      if (!packageMatchesPlatform(platform, name[3], format)) continue;
      const expectedUrl = `${repository}/releases/download/${entry.tag_name}/${asset.name}`;
      if (asset.browser_download_url !== expectedUrl || !/^sha256:[a-f0-9]{64}$/.test(asset.digest ?? '')) continue;
      packages.push({ platform, architecture: name[3], format, url: expectedUrl, filename: asset.name, sha256: asset.digest.slice(7) });
    }
    // A partial upload must not replace a complete release in the website.
    if (!hasCompleteTargetSet(packages)) continue;
    candidates.push({release:{ version: tag[1], published:true, preview:entry.prerelease, releaseUrl:`${repository}/releases/tag/${entry.tag_name}`, packages:packages.filter(item=>item.format!=='appimage') }, version:[...tag[1].split('.').map(Number),tag[2] ? Number(tag[2]) : Number.MAX_SAFE_INTEGER]});
  }
  candidates.sort((a,b) => {
    for (let index=0;index<4;index++) if (a.version[index] !== b.version[index]) return b.version[index]-a.version[index];
    return 0;
  });
  return candidates[0]?.release ?? null;
}
