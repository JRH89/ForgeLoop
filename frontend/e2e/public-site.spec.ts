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
  await expect(page.locator('.article-card')).toHaveCount(4);
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

test('public navbar stays pinned and anchor content stays unobscured',async({page})=>{
  for(const width of [390,1000,1440]){
    await page.setViewportSize({width,height:844});
    await page.goto('/');
    const header=page.locator('.public-nav');
    const height=(await header.boundingBox())!.height;
    await page.getByRole('link',{name:'See the delivery loop'}).click();
    await expect.poll(async()=>Math.round((await header.boundingBox())!.y)).toBe(0);
    expect((await page.locator('#how').boundingBox())!.y).toBeGreaterThanOrEqual(height);
    await page.evaluate(()=>window.scrollTo(0,document.body.scrollHeight));
    await expect.poll(async()=>Math.round((await header.boundingBox())!.y)).toBe(0);
    if(width<=1000){
      await page.getByRole('button',{name:'Open navigation'}).click();
      const panel=page.locator('#mobile-menu');
      await expect(panel).toBeVisible();
      await expect.poll(async()=>Math.round((await panel.boundingBox())!.y)).toBe(height-1);
      await panel.getByRole('link',{name:'Features',exact:true}).click();
      await expect(page).toHaveURL(/\/features$/);
    }
  }
});

test('static assets, sitemap, and canonical redirects work',async({request})=>{
  for(const path of ['/robots.txt','/sitemap.xml','/images/social-preview.jpg','/images/delivery-hero.webp','/icons/icon-32.png'])expect((await request.get(path)).status()).toBe(200);
  const redirect=await request.get('/about/',{maxRedirects:0});expect(redirect.status()).toBe(308);
  expect(redirect.headers().location).toMatch(/\/about$/);
});

test('about page omits the studio credit and footer has no studio link',async({page})=>{
  await page.goto('/about');
  await expect(page.getByRole('heading',{name:'Built by Hooker Hill Studios',exact:true})).toHaveCount(0);
  await expect(page.locator('.footer-note a[href="https://hookerhillstudios.com"]')).toHaveCount(0);
  await expect(page.locator('.footer-note')).toContainText('© 2026 Hooker Hill Studios.');
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

test('new runner recovery article has truthful date and unique search/social metadata',async({page})=>{
  await page.goto('/blog/self-hosted-ai-runner-restart-recovery');
  await expect(page.locator('time[datetime="2026-09-27"]')).toHaveText('September 27, 2026');
  await expect(page.locator('meta[name="description"]')).toHaveAttribute('content',/restart recovery checklist/);
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content',/self-hosted-ai-runner-restart-recovery-social-preview\.jpg$/);
  await expect(page.locator('.markdown-content')).toContainText('A heartbeat reports that a runner is alive');
  const schema=JSON.parse(await page.locator('script[type="application/ld+json"]').innerText());
  expect(schema['@graph'].find((entry:{'@type':string})=>entry['@type']==='BlogPosting').datePublished).toBe('2026-09-27');
  await page.setViewportSize({width:390,height:844});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
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
  for(const width of [320,390,430,760,768,1000,1440]){
    await page.setViewportSize({width,height:1000});
    const layout=await page.locator('.landing-hero').evaluate(el=>{
      const css=getComputedStyle(el),hero=el.getBoundingClientRect();
      const eyebrow=el.querySelector('.eyebrow')!.getBoundingClientRect();
      const heading=el.querySelector('h1')!,headingBox=heading.getBoundingClientRect();
      const description=el.querySelector(':scope > p:not(.eyebrow)')!,descriptionBox=description.getBoundingClientRect();
      const actions=el.querySelector('.landing-actions')!.getBoundingClientRect();
      const top=parseFloat(css.paddingTop),bottom=parseFloat(css.paddingBottom);
      return {
        padding:[css.paddingTop,css.paddingBottom],display:css.display,direction:css.flexDirection,
        alignment:css.justifyContent,minHeight:parseFloat(css.minHeight),viewportHeight:innerHeight,
        headerHeight:document.querySelector('.public-nav')!.getBoundingClientRect().height,
        headlineGap:descriptionBox.top-headingBox.bottom,
        headingMarginBottom:getComputedStyle(heading).marginBottom,
        descriptionMarginTop:getComputedStyle(description).marginTop,
        actionMarginTop:getComputedStyle(el.querySelector('.landing-actions')!).marginTop,
        contentCenter:(eyebrow.top+actions.bottom)/2,
        contentAreaCenter:(hero.top+top+hero.bottom-bottom)/2,
      };
    });
    expect(layout.padding).toEqual(width<=1000?['24px','40px']:['110px','85px']);
    expect(layout.actionMarginTop).toBe(width<=760?'16px':'32px');
    expect(layout.headingMarginBottom).toBe(width<=760?'13px':'26px');
    expect(layout.descriptionMarginTop).toBe(width<=760?'8px':'16px');
    if(width<=760)expect(layout.headlineGap).toBe(21);
    if(width<=760){
      const footer=await page.locator('.public-footer').boundingBox();
      const note=await page.locator('.footer-note').boundingBox();
      expect(Math.round(footer!.y+footer!.height-note!.y-note!.height)).toBe(20);
    }
    if(width<=1000){
      expect(layout.display).toBe('flex');
      expect(layout.direction).toBe('column');
      expect(layout.alignment).toBe('center');
      expect(layout.minHeight).toBeGreaterThanOrEqual(layout.viewportHeight-layout.headerHeight-1);
      expect(Math.abs(layout.contentCenter-layout.contentAreaCenter)).toBeLessThanOrEqual(20);
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    }
  }
});
