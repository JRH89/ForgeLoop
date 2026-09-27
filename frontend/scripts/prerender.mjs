import { readFile,writeFile,mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';
import { render, metadata, publicPaths, articles, SITE } from '../.prerender/prerender.js';
const template=await readFile('dist/index.html','utf8');
const escape=value=>String(value).replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;').replaceAll('>','&gt;');
function document(path,app=false) {
  const info=metadata(path);
  const title=app?'ForgeLoop console':info.title;
  const robots=app||!info.found?'noindex, nofollow':'index, follow, max-image-preview:large';
  const social=SITE+info.hero.social;
  const head=`<title>${escape(title)}</title>
<meta name="description" content="${escape(info.description)}"/>
<meta name="robots" content="${robots}"/>
${app?'':`<link rel="canonical" href="${info.url}"/>`}
<meta property="og:site_name" content="ForgeLoop"/>
<meta property="og:type" content="${info.article?'article':'website'}"/>
<meta property="og:title" content="${escape(title)}"/>
<meta property="og:description" content="${escape(info.description)}"/>
<meta property="og:url" content="${info.url}"/>
<meta property="og:locale" content="en_US"/>
<meta property="og:image" content="${social}"/>
<meta property="og:image:width" content="1200"/>
<meta property="og:image:height" content="630"/>
<meta property="og:image:alt" content="${escape(title)} — editorial illustration"/>
<meta name="twitter:card" content="summary_large_image"/>
<meta name="twitter:title" content="${escape(title)}"/>
<meta name="twitter:description" content="${escape(info.description)}"/>
<meta name="twitter:image" content="${social}"/>
<meta name="twitter:image:alt" content="${escape(title)} — editorial illustration"/>
${info.article?`<meta property="article:published_time" content="${info.article.published}"/><meta property="article:modified_time" content="${info.article.published}"/>`:''}
${app?'':`<link rel="preload" as="image" href="${info.hero.image}" media="(min-width: 761px)"/><link rel="preload" as="image" href="${info.hero.small}" media="(max-width: 760px)"/>`}
${app||!info.found?'':`<script type="application/ld+json">${JSON.stringify(info.jsonLd).replaceAll('<','\\u003c')}</script>`}`;
  return template.replace(/<title>[\s\S]*?<\/title>/,'').replace(/<meta\s+(?:name="description"|property="og:[^"]+")[^>]*>/g,'').replace('</head>',`${head}</head>`).replace('<div id="root"></div>',`<div id="root">${app?'':render(path)}</div>`);
}
for(const path of publicPaths){const file=path==='/'?'dist/index.html':`dist${path}.html`;await mkdir(dirname(file),{recursive:true});await writeFile(file,document(path));}
await writeFile('dist/404.html',document('/404'));
await writeFile('dist/app-shell.html',document('/app',true));
await writeFile('dist/robots.txt',`User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /graphql\nDisallow: /oauth2/\nDisallow: /login/\nDisallow: /downloads/\nSitemap: ${SITE}/sitemap.xml\n`);
await writeFile('dist/sitemap.xml',`<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${publicPaths.map(path=>`<url><loc>${SITE}${path==='/'?'/':path}</loc>${articles.find(article=>path===`/blog/${article.slug}`)?`<lastmod>${articles.find(article=>path===`/blog/${article.slug}`).published}</lastmod>`:''}</url>`).join('')}</urlset>`);
console.log(`Prerendered ${publicPaths.length} public pages, sitemap, robots, app shell, and 404.`);
