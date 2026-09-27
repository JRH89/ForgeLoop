import { expect, test } from '@playwright/test';
import { publicPaths } from '../src/public/seo';
import AxeBuilder from '@axe-core/playwright';
import { articles } from '../src/public/articles';

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
  await page.getByRole('button',{name:'Open navigation'}).click();
  await page.getByRole('navigation',{name:'Mobile navigation'}).getByRole('link',{name:'Blog',exact:true}).click();
  await expect(page.getByRole('heading',{level:1})).toContainText('Better systems');
  for(const path of ['/','/features','/about','/docs','/blog','/blog/self-hosted-ai-runners']){
    await page.goto(path);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),path).toBe(true);
  }
  await page.screenshot({path:'../evidence/public-site/article-mobile.png',fullPage:true});
  expect(errors).toEqual([]);expect(tenantRequests).toEqual([]);
});

test('full-width chrome and console-style mobile dropdown',async({page})=>{
  await page.setViewportSize({width:1920,height:1000});
  await page.goto('/');
  for(const selector of ['.public-nav','.public-footer']){
    expect((await page.locator(selector).boundingBox())?.width).toBe(1920);
  }
  const nav=await page.getByRole('navigation',{name:'Main navigation',exact:true}).boundingBox();
  expect(Math.abs(nav!.x+nav!.width/2-960)).toBeLessThan(2);
  const toggle=page.locator('.menu-toggle');
  const panel=page.locator('#mobile-menu');
  for(const width of [320,390,760,1000]){
    await page.setViewportSize({width,height:844});
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded','true');
    await expect(panel).toHaveClass(/is-open/);
    await expect(panel).toHaveAttribute('aria-hidden','false');
    await expect(toggle).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(panel.getByRole('link',{name:'Features',exact:true})).toBeFocused();
    // Absolute positioning starts at the header's inner border edge.
    await expect.poll(async()=>Math.round((await panel.boundingBox())!.y)).toBe(75);
    const box=(await panel.boundingBox())!;
    expect(box.x).toBe(0);expect(box.width).toBe(width);
    expect(await panel.evaluate(el=>getComputedStyle(el).backgroundColor)).toBe('rgb(11, 24, 36)');
    expect(await panel.evaluate(el=>getComputedStyle(el).transitionDuration)).toContain('0.2s');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    if(width===390){
      expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);
      await page.screenshot({path:'../evidence/public-site/mobile-dropdown.png'});
    }
    await page.keyboard.press('Escape');
    await expect(panel).not.toBeVisible();
    await expect(toggle).toBeFocused();
    expect(await panel.evaluate(el=>el.inert)).toBe(true);
  }
  await page.setViewportSize({width:390,height:844});
  await toggle.click();
  await page.mouse.click(5,800);
  await expect(panel).not.toBeVisible();
  await toggle.click();
  await panel.getByRole('link',{name:'Sign in with GitHub'}).focus();
  await page.keyboard.press('Tab');
  await expect(panel).not.toBeVisible();
  await page.emulateMedia({reducedMotion:'reduce'});
  await toggle.click();
  expect(await panel.evaluate(el=>getComputedStyle(el).transitionDuration)).toBe('0s');
  await toggle.click();
  await expect(panel).not.toBeVisible();
  await toggle.click();
  await page.setViewportSize({width:1440,height:1000});
  await expect(panel).not.toBeVisible();
  await page.goto('/features');
  await page.setViewportSize({width:390,height:500});
  await toggle.click();
  await expect(panel.getByRole('link',{name:'Features',exact:true})).toHaveAttribute('aria-current','page');
  await panel.getByRole('link',{name:'Contact',exact:true}).click();
  await expect(page).toHaveURL(/\/contact$/);
});

test('static assets, sitemap, and canonical redirects work',async({request})=>{
  for(const path of ['/robots.txt','/sitemap.xml','/images/social-preview.jpg','/images/delivery-hero.webp','/icons/icon-32.png'])expect((await request.get(path)).status()).toBe(200);
  const redirect=await request.get('/about/',{maxRedirects:0});expect(redirect.status()).toBe(308);
  expect(redirect.headers().location).toMatch(/\/about$/);
});

test('article cards open from their read action and every page has distinct artwork',async({page,request})=>{
  for(const article of articles){
    await page.goto('/blog');
    const card=page.locator(`a.article-card[href="/blog/${article.slug}"]`);
    await card.getByText('Read field note').click();
    await expect(page).toHaveURL(new RegExp(`/blog/${article.slug}$`));
    await expect(page.getByRole('heading',{level:1})).toHaveText(article.title);
    await expect(page.locator('.markdown-content')).toBeVisible();
  }
  const images=new Set<string>();
  for(const path of publicPaths){
    await page.goto(path);
    await expect(page.locator('main')).not.toContainText(/Ticketly/i);
    await expect(page.locator('.landing-terminal')).toHaveCount(0);
    const image=await page.locator('.public-hero,.landing-hero').evaluate(el=>getComputedStyle(el).getPropertyValue('--hero-image'));
    expect(images.has(image),path).toBe(false);images.add(image);
    const url=image.match(/url\("?([^"\s)]+)/)?.[1];
    expect(url,path).toBeTruthy();
    expect((await request.get(url!)).status()).toBe(200);
  }
});

test('public templates pass automated accessibility checks',async({page})=>{
  for(const path of ['/','/features','/blog','/blog/self-hosted-ai-runners']){
    await page.goto(path);
    const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
    expect(result.violations,path).toEqual([]);
  }
});

test('hero overlays protect text below large desktop widths',async({page})=>{
  for(const path of ['/','/features','/blog/self-hosted-ai-runners','/this-page-does-not-exist']){
    await page.goto(path);
    for(const width of [390,768,1024,1280,1439,1440,1920]){
      await page.setViewportSize({width,height:900});
      const hero=page.locator('.landing-hero,.public-hero');
      const style=await hero.evaluate(el=>{
        const css=getComputedStyle(el);
        return {start:Number(css.getPropertyValue('--hero-overlay-start')),end:Number(css.getPropertyValue('--hero-overlay-end')),background:css.backgroundImage};
      });
      expect(style.start,`${path} at ${width}`).toBe(width>=1440?0:width<=760?.96:.94);
      expect(style.end,`${path} at ${width}`).toBe(width>=1440?0:width<=760?.88:.8);
      expect(style.background).toContain('linear-gradient');
      expect(style.background).toContain(width<=760?'-hero-small.webp':'-hero.webp');
      await expect(page.getByRole('heading',{level:1})).toBeVisible();
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
      if(path==='/'&&[390,1280,1440].includes(width))await page.screenshot({path:`../evidence/public-site/hero-overlay-${width}.png`});
    }
  }
});

test('landing hero uses the refined reference-inspired artwork',async({page,request})=>{
  await page.goto('/');
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content',/delivery-v2-social-preview.jpg$/);
  await expect(page.locator('.landing-hero')).toHaveAttribute('style',/delivery-v2-hero.webp/);
  for(const file of ['delivery-v2-hero.webp','delivery-v2-hero-small.webp','delivery-v2-social-preview.jpg']){
    expect((await request.get(`/images/${file}`)).status()).toBe(200);
  }
  for(const width of [320,390,430,760,1440]){
    await page.setViewportSize({width,height:1000});
    const padding=await page.locator('.landing-hero').evaluate(el=>{
      const css=getComputedStyle(el);return [css.paddingTop,css.paddingBottom];
    });
    expect(padding).toEqual(width<=760?['32px','65px']:['110px','85px']);
    if(width<=760){
      // Measure the visible gap, not just padding, to catch margin/layout regressions.
      const header=await page.locator('.public-nav').boundingBox();
      const eyebrow=await page.locator('.landing-hero .eyebrow').boundingBox();
      const gap=eyebrow!.y-(header!.y+header!.height);
      expect(gap).toBeGreaterThanOrEqual(32);
      expect(gap).toBeLessThanOrEqual(48);
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    }
  }
});
