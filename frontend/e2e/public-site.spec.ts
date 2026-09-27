import { expect, test } from '@playwright/test';
import { publicPaths } from '../src/public/seo';
import AxeBuilder from '@axe-core/playwright';

test('public pages serve content and social metadata without JavaScript',async({browser})=>{
  const context=await browser.newContext({javaScriptEnabled:false});
  const page=await context.newPage();
  for(const path of publicPaths){
    const response=await page.goto(path);
    expect(response?.status(),path).toBe(200);
    await expect(page.locator('h1')).toHaveCount(1);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content',/social-preview\.jpg$/);
    expect((await page.locator('main').innerText()).length).toBeGreaterThan(400);
  }
  const missing=await page.goto('/this-page-does-not-exist');
  expect(missing?.status()).toBe(404);
  await expect(page.locator('h1')).toContainText('This path');
  await context.close();
});

test('public navigation is usable on desktop and mobile without tenant requests',async({page})=>{
  const errors:string[]=[];const tenantRequests:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('request',request=>{if(request.url().endsWith('/graphql'))tenantRequests.push(request.url());});
  await page.setViewportSize({width:1440,height:1000});
  await page.goto('/');
  await expect(page.getByRole('heading',{name:/Turn GitHub issues into/})).toBeVisible();
  await page.getByRole('navigation',{name:'Main navigation',exact:true}).getByRole('link',{name:'Features',exact:true}).click();
  await expect(page.getByRole('heading',{level:1})).toContainText('More than an agent');
  await page.screenshot({path:'../evidence/public-site/features-desktop.png',fullPage:true});
  await page.goto('/');await page.screenshot({path:'../evidence/public-site/home-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.getByText('Menu',{exact:true}).click();
  await page.getByRole('navigation',{name:'Mobile navigation'}).getByRole('link',{name:'Blog',exact:true}).click();
  await expect(page.getByRole('heading',{level:1})).toContainText('Better systems');
  for(const path of ['/','/features','/about','/docs','/blog','/blog/self-hosted-ai-runners']){
    await page.goto(path);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),path).toBe(true);
  }
  await page.screenshot({path:'../evidence/public-site/article-mobile.png',fullPage:true});
  expect(errors).toEqual([]);expect(tenantRequests).toEqual([]);
});

test('static assets, sitemap, and canonical redirects work',async({request})=>{
  for(const path of ['/robots.txt','/sitemap.xml','/images/social-preview.jpg','/images/delivery-hero.webp','/icons/icon-32.png'])expect((await request.get(path)).status()).toBe(200);
  const redirect=await request.get('/about/',{maxRedirects:0});expect(redirect.status()).toBe(308);
  expect(redirect.headers().location).toMatch(/\/about$/);
});

test('public templates pass automated accessibility checks',async({page})=>{
  for(const path of ['/','/features','/blog','/blog/self-hosted-ai-runners']){
    await page.goto(path);
    const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
    expect(result.violations,path).toEqual([]);
  }
});
