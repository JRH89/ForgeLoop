import { expect, test } from '@playwright/test';

test('public downloads show versioned installers and optional verification without tenant calls',async({page})=>{
  const tag='desktop-v1.0.4-preview.1';
  const assets=['windows-x64.msi','windows-arm64.msi','macos-arm64.dmg','macos-x64.dmg',
    'linux-x64.deb','linux-x64.rpm','linux-x64.tar.gz','linux-x64.pkg.tar.zst',
    'linux-arm64.deb','linux-arm64.rpm','linux-arm64.tar.gz','linux-arm64.pkg.tar.zst'].map(target=>({
    name:`forgeloop-runner-1.0.4-${target}`,state:'uploaded',size:1024,digest:`sha256:${'a'.repeat(64)}`,
    browser_download_url:`https://github.com/JRH89/ForgeLoop/releases/download/${tag}/forgeloop-runner-1.0.4-${target}`
  }));
  const tenantRequests:string[]=[];
  page.on('request',request=>{if(request.url().endsWith('/graphql'))tenantRequests.push(request.url());});
  await page.route('**/downloads/desktop-releases.json',route=>route.fulfill({json:[{tag_name:tag,prerelease:true,draft:false,published_at:'2026-09-27',assets}]}));
  for(const width of [390,1440]){
    await page.setViewportSize({width,height:900});
    await page.goto('/app/runner-downloads');
    await expect(page.getByRole('heading',{name:'Version 1.0.4 — Preview'})).toBeVisible();
    await expect(page.getByRole('link',{name:/Download for/})).toHaveCount(12);
    await expect(page.getByRole('heading',{name:'Linux x64 · RPM'})).toBeVisible();
    await expect(page.getByRole('heading',{name:'Linux x64 · Arch package'})).toBeVisible();
    await expect(page.getByRole('heading',{name:'Windows (arm64)'})).toBeVisible();
    await expect(page.getByText(/This is a native pacman package, not an AppImage/)).toHaveCount(2);
    await page.getByText('Verify download',{exact:true}).first().click();
    await expect(page.getByText(/Get-FileHash/)).toHaveCount(2);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
  expect(tenantRequests).toEqual([]);
});
