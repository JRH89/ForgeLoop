import { expect, it } from 'vitest';
import { SITE } from './content';
import { articles } from './articles';
import { metadata, publicPaths } from './seo';
import nginx from '../../nginx.conf?raw';

it('redirects legacy public pages without redirecting runner or authentication requests',()=>{
  const pattern=nginx.match(/~(\S+) 1;/)?.[1];
  expect(pattern).toBeTruthy();
  const redirects=new RegExp(pattern!);
  for(const method of ['GET','HEAD'])for(const path of ['/','/blog','/blog/github-app-vs-pat-ai-coding-agents','/docs','/robots.txt','/sitemap.xml']){
    expect(redirects.test(`forgeloop.hookerhillstudios.com:${method}:${path}`)).toBe(true);
    expect(redirects.test(`forgeloop.codefrontlabs.com:${method}:${path}`)).toBe(false);
  }
  for(const path of ['/api/runner/heartbeat','/graphql','/oauth2/authorization/github','/login/oauth2/code/github','/api/github/webhooks','/app/runner-connect','/app/runner-downloads']){
    for(const method of ['GET','HEAD','POST'])expect(redirects.test(`forgeloop.hookerhillstudios.com:${method}:${path}`)).toBe(false);
  }
  expect(redirects.test('forgeloop.hookerhillstudios.com:POST:/blog')).toBe(false);
});

it('uses the approved canonical host for every public metadata graph',()=>{
  expect(SITE).toBe('https://forgeloop.codefrontlabs.com');
  for(const path of publicPaths){
    const info=metadata(path);
    expect(info.url).toBe(SITE+path);
    expect(JSON.stringify(info.jsonLd)).not.toContain('forgeloop.hookerhillstudios.com');
    expect(JSON.stringify(info.jsonLd)).not.toContain('Hooker Hill Studios');
    expect(info.jsonLd['@graph'].find(item=>item['@type']==='Organization')?.name).toBe('Codefront Labs');
    expect(info.jsonLd['@graph'].find(item=>item['@type']==='WebSite')?.url).toBe(SITE+'/');
  }
});

it('adds a distinct authorization article with matching social art and useful internal links',()=>{
  const article=articles.find(item=>item.slug==='github-app-vs-pat-ai-coding-agents')!;
  expect(article.published).toBe('2026-10-15');
  expect(new Set(articles.map(item=>item.slug)).size).toBe(articles.length);
  expect(article.body).toContain('## A first-run checklist');
  expect(article.body).toContain('https://docs.github.com/');
  expect(article.body).toContain('](/docs)');
  const info=metadata('/blog/'+article.slug);
  const schema=info.jsonLd['@graph'].find(item=>item['@type']==='BlogPosting');
  expect(schema?.url).toBe(SITE+'/blog/'+article.slug);
  expect(schema?.image).toEqual([SITE+'/images/'+article.slug+'-social-preview.jpg']);
});
