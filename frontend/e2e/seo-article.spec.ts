import { expect, test } from '@playwright/test';

test('legacy-host redirects preserve public paths and queries but exclude operational endpoints',async({request})=>{
  const headers={Host:'forgeloop.hookerhillstudios.com'};
  const result=await request.get('/blog/github-app-vs-pat-ai-coding-agents?source=guide',{headers,maxRedirects:0});
  expect(result.status()).toBe(308);
  expect(result.headers().location).toBe('https://forgeloop.codefrontlabs.com/blog/github-app-vs-pat-ai-coding-agents?source=guide');
  const pairing=await request.get('/app/runner-connect',{headers,maxRedirects:0});
  expect(pairing.status()).toBe(200);
  const api=await request.head('/api/runner/not-a-real-endpoint',{headers,maxRedirects:0});
  expect(api.status()).not.toBe(308);
  const post=await request.post('/docs',{headers,maxRedirects:0});
  expect(post.status()).not.toBe(308);
});

test('larger public navbar brand stays balanced without enlarging footer branding',async({page})=>{
  await page.goto('/');
  for(const width of [320,390,1000,1001,1440]){
    await page.setViewportSize({width,height:900});
    const brand=page.locator('.public-nav>.brand');
    await expect(brand.locator('img')).toHaveCSS('width',width<=1000?'44px':'50px');
    await expect(brand.locator('strong')).toHaveCSS('font-size',width<=1000?'25px':'28px');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    const rightControl=page.locator(width<=1000?'.menu-toggle':'.public-nav>.primary');
    const left=await brand.boundingBox(),right=await rightControl.boundingBox();
    expect(left!.x+left!.width).toBeLessThan(right!.x);
  }
  await expect(page.locator('.public-footer .brand img')).toHaveCSS('width','30px');
  await page.screenshot({path:'../evidence/public-site/larger-navbar-brand.png'});
});

test('footer groups sit in the middle of their columns with left-aligned links',async({page})=>{
  await page.goto('/');
  const groups=page.locator('.public-footer>nav');
  for(const width of [390,760,761,1000,1440]){
    await page.setViewportSize({width,height:900});
    for(const group of await groups.all()){
      if(width>760)await expect(group).toHaveCSS('justify-self','center');
      else await expect(group).not.toHaveCSS('justify-self','center');
      if(width>760)await expect(group).toHaveCSS('text-align','left');
    }
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
  await page.locator('.public-footer').screenshot({path:'../evidence/public-site/footer-column-centering.png'});
});

test('authorization article is crawlable, linked, and readable at desktop and mobile widths',async({browser})=>{
  const context=await browser.newContext({javaScriptEnabled:false});
  const page=await context.newPage();
  const path='/blog/github-app-vs-pat-ai-coding-agents';
  await page.goto(path);
  await expect(page.locator('h1')).toHaveText('GitHub App vs PAT for AI coding agents');
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href','https://forgeloop.codefrontlabs.com'+path);
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute('content','https://forgeloop.codefrontlabs.com'+path);
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content',/codefrontlabs.com\/images\/github-app-vs-pat-ai-coding-agents-social-preview.jpg$/);
  await expect(page.getByRole('heading',{name:'A first-run checklist for an AI coding runner'})).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(1);
  for(const width of [390,1440]){
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:`../evidence/public-site/authorization-article-${width}.png`,fullPage:true});
  }
  await page.goto('/blog');
  await expect(page.getByRole('link',{name:/GitHub App vs PAT for AI coding agents/})).toBeVisible();
  await context.close();
});
