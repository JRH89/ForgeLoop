import { articles } from './articles';
import { pages, SITE } from './content';
import { heroFor } from './heroes';

export const publicPaths=[...pages.map(page=>page.path),...articles.map(article=>`/blog/${article.slug}`)];
/** One metadata source serves static HTML, sitemap generation, and regression tests. */
export function metadata(path:string) {
  const article=articles.find(item=>path===`/blog/${item.slug}`);
  const page=pages.find(item=>item.path===path);
  const title=path==='/support'?'Support center | ForgeLoop':article?`${article.title} | ForgeLoop`:page?.title??'Page not found | ForgeLoop';
  const description=path==='/support'?'Track your ForgeLoop support requests, read replies, and follow ticket status securely.':article?.description??page?.description??'This page could not be found. Explore ForgeLoop features, documentation, and practical software delivery guides.';
  const url=SITE+(path==='/'?'/':path);
  const organization={'@type':'Organization','@id':`${SITE}/#organization`,name:'Hooker Hill Studios',url:`${SITE}/about`,logo:{'@type':'ImageObject',url:`${SITE}/icons/icon-192.png`,width:192,height:192}};
  const graph:Record<string,unknown>[]=[organization,{'@type':'WebSite','@id':`${SITE}/#website`,name:'ForgeLoop',url:`${SITE}/`,publisher:{'@id':`${SITE}/#organization`}},
    {'@type':article?'WebPage':path==='/about'?'AboutPage':path==='/contact'?'ContactPage':path==='/blog'?'CollectionPage':'WebPage','@id':`${url}#page`,url,name:title,description,isPartOf:{'@id':`${SITE}/#website`}}];
  if(article)graph.push({'@type':'BlogPosting',headline:article.title,description,datePublished:article.published,dateModified:article.published,author:{'@type':'Organization',name:'Hooker Hill Studios',url:`${SITE}/about`},publisher:{'@id':`${SITE}/#organization`},mainEntityOfPage:{'@id':`${url}#page`},image:[`${SITE}/images/social-preview.jpg`],articleSection:article.category});
  if(path==='/')graph.push({'@type':'SoftwareApplication',name:'ForgeLoop',applicationCategory:'DeveloperApplication',operatingSystem:'Web; Windows; macOS; Linux',url:`${SITE}/`,description:page?.description});
  if(path!=='/'&&(page||article))graph.push({'@type':'BreadcrumbList',itemListElement:[{'@type':'ListItem',position:1,name:'Home',item:`${SITE}/`},...(article?[{'@type':'ListItem',position:2,name:'Field notes',item:`${SITE}/blog`},{'@type':'ListItem',position:3,name:article.title,item:url}]:[{'@type':'ListItem',position:2,name:page?.heading,item:url}])]});
  const hero=heroFor(page||article?path:'/not-found');
  for(const entry of graph)if(entry['@type']==='BlogPosting')entry.image=[SITE+hero.social];
  return {title,description,url,article,hero,found:!!(page||article),jsonLd:{'@context':'https://schema.org','@graph':graph}};
}
