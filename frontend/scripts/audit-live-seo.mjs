import { JSDOM } from 'jsdom';

// Read-only deployment check. Fetch the requested host even if its sitemap advertises an old one.
const origin=new URL(process.argv[2]??'https://forgeloop.codefrontlabs.com').origin;
const issues=[];
async function get(path){return fetch(origin+path,{signal:AbortSignal.timeout(20000)});}
const robots=await get('/robots.txt');
if(!robots.ok||!(await robots.text()).includes(`Sitemap: ${origin}/sitemap.xml`))issues.push('robots: canonical sitemap missing');
const sitemapResponse=await get('/sitemap.xml');
if(!sitemapResponse.ok)throw new Error('Sitemap unavailable');
const sitemap=await sitemapResponse.text();
const locations=[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match=>new URL(match[1]));
if(!locations.length)throw new Error('Sitemap has no public URLs');
const titles=new Set(),descriptions=new Set();
let pagesChecked=0;
for(const location of locations){
  const path=location.pathname,expected=origin+path;
  if(location.origin!==origin)issues.push(`${path}: sitemap uses ${location.origin}`);
  const response=await get(path),html=await response.text();
  const doc=new JSDOM(html).window.document;
  const canonical=doc.querySelector('link[rel="canonical"]')?.href;
  if(response.status!==200)issues.push(`${path}: HTTP ${response.status}`);
  if(canonical!==expected)issues.push(`${path}: canonical ${canonical}`);
  if(doc.querySelector('a[href*="hookerhillstudios.com"]')||/Hooker Hill Studios/.test(doc.body.textContent))issues.push(`${path}: obsolete publisher/link`);
  if(doc.querySelector('meta[property="og:url"]')?.content!==expected)issues.push(`${path}: Open Graph URL mismatch`);
  if(doc.querySelectorAll('h1').length!==1)issues.push(`${path}: expected one H1`);
  const description=doc.querySelector('meta[name="description"]')?.content;
  if(!doc.title||titles.has(doc.title))issues.push(`${path}: missing/duplicate title`);
  if(!description||descriptions.has(description))issues.push(`${path}: missing/duplicate description`);
  titles.add(doc.title);descriptions.add(description);
  const image=doc.querySelector('meta[property="og:image"]')?.content;
  if(!image||new URL(image).origin!==origin)issues.push(`${path}: social image uses wrong origin`);
  const schema=doc.querySelector('script[type="application/ld+json"]')?.textContent;
  try{JSON.parse(schema??'');}catch{issues.push(`${path}: missing/invalid structured data`);}
  pagesChecked++;
}
const missing=await get('/not-a-real-forgeloop-seo-audit-page');
if(missing.status!==404)issues.push(`Missing page returned ${missing.status}, expected 404`);
console.log(JSON.stringify({origin,pagesChecked,issueCount:issues.length,issues},null,2));
if(issues.length)process.exitCode=1;
