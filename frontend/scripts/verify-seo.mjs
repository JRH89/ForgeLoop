import { readFile, stat } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { publicPaths, articles, SITE, metadata } from '../.prerender/prerender.js';
const titles=new Set(),descriptions=new Set(),heroes=new Set();
assert.equal(SITE,'https://forgeloop.codefrontlabs.com','Canonical origin must remain the approved public domain');
for(const path of publicPaths){
  const html=await readFile(path==='/'?'dist/index.html':`dist${path}.html`,'utf8');
  const doc=new JSDOM(html).window.document;
  const title=doc.title,description=doc.querySelector('meta[name="description"]')?.content;
  assert(title.length<=60&&title.length>=15,`${path}: title length ${title.length}`);
  assert(description?.length<=160&&description?.length>=90,`${path}: description length ${description?.length}`);
  assert(!titles.has(title)&&!descriptions.has(description),`${path}: duplicated metadata`);titles.add(title);descriptions.add(description);
  assert.equal(doc.querySelectorAll('h1').length,1,`${path}: one main heading`);
  assert.equal(doc.querySelector('link[rel="canonical"]').href,SITE+path);
  assert.equal(doc.querySelector('meta[property="og:title"]').content,title);
  assert.equal(doc.querySelector('meta[property="og:url"]').content,SITE+path);
  assert.equal(doc.querySelector('meta[name="robots"]').content,'index, follow, max-image-preview:large');
  assert(!html.includes('forgeloop.hookerhillstudios.com'),`${path}: obsolete public origin`);
  assert(!/hooker\s*hill\s*studios|hookerhillstudios\.com/i.test(html),`${path}: obsolete publisher or link`);
  assert.equal(doc.querySelector('meta[name="twitter:card"]').content,'summary_large_image');
  const hero=metadata(path).hero;
  assert.equal(doc.querySelector('meta[property="og:image"]').content,SITE+hero.social);
  assert(!heroes.has(hero.image),`${path}: duplicate hero`);heroes.add(hero.image);
  for(const asset of Object.values(hero))assert((await stat(`dist${asset}`)).size<500_000,`${path}: missing or oversized image`);
  assert(!/ticketly/i.test(doc.querySelector('main').textContent),`${path}: internal demo reference`);
  assert(!doc.querySelector('.landing-terminal'),`${path}: removed status strip`);
  const schema=JSON.parse(doc.querySelector('script[type="application/ld+json"]').textContent);
  assert.equal(schema['@context'],'https://schema.org');
  if(metadata(path).article){
    const posting=schema['@graph'].find(entry=>entry['@type']==='BlogPosting');
    assert.equal(posting.url,SITE+path);
    assert.equal(posting.mainEntityOfPage['@id'],SITE+path+'#page');
  }
  assert(doc.querySelector('main').textContent.length>400,`${path}: real prerendered content`);
  for(const link of doc.querySelectorAll('a[href]')){
    const href=link.getAttribute('href');
    if(href.startsWith('/')&&!href.startsWith('/app')&&!href.startsWith('/oauth2')&&!href.startsWith('/support'))assert(publicPaths.includes(href.split('#')[0]),`${path}: broken local link ${href}`);
  }
  assert(doc.querySelector('.public-hero,.landing-hero'),`${path}: missing hero`);
}
assert(articles.length>=11,'Expected the original ten articles and a new field note');
assert.equal(new Set(articles.map(article=>article.slug)).size,articles.length,'Article slugs must be unique');
for(const article of articles){assert(article.body.trim().split(/\s+/).length>=280,`${article.slug}: thin content`);assert(/^\d{4}-\d{2}-\d{2}$/.test(article.published),`${article.slug}: invalid publication date`);}
assert((await stat('dist/images/delivery-hero.webp')).size<500_000,'Hero exceeds image budget');
assert((await readFile('dist/app-shell.html','utf8')).includes('noindex, nofollow'));
assert((await readFile('dist/404.html','utf8')).includes('noindex, nofollow'));
const sitemap=await readFile('dist/sitemap.xml','utf8');
assert.equal((sitemap.match(/<loc>/g)||[]).length,publicPaths.length);
assert(!sitemap.includes('/app'));
assert(!sitemap.includes('forgeloop.hookerhillstudios.com'));
const robots=await readFile('dist/robots.txt','utf8');
assert(robots.includes(`Sitemap: ${SITE}/sitemap.xml`));
console.log(`SEO checks passed: ${publicPaths.length} pages; ${articles.length} substantive articles; metadata, schema, links, and image budgets.`);
