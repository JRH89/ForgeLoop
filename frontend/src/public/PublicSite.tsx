import LandingPage from '../LandingPage';
import MarkdownContent from '../MarkdownContent';
import { articles } from './articles';
import { pages, REPOSITORY } from './content';
import { PublicLayout, PublicHero } from './PublicLayout';
import './public.css';

export function ArticleCards({limit}:{limit?:number}) {
  return <div className="article-grid">{articles.slice(0,limit).map(article=><article className="article-card" key={article.slug}><p className="eyebrow">{article.category}</p><h3><a href={`/blog/${article.slug}`}>{article.title}</a></h3><p>{article.description}</p><span>Read field note <span aria-hidden="true">↗</span></span></article>)}</div>;
}
export default function PublicSite({path}:{path:string}) {
  if(path==='/')return <LandingPage/>;
  const article=articles.find(item=>path===`/blog/${item.slug}`);
  if(article)return <PublicLayout><PublicHero eyebrow={article.category} heading={article.title} intro={article.description}/><article className="article-body"><nav aria-label="Breadcrumb"><a href="/">Home</a> / <a href="/blog">Field notes</a> / <span>{article.title}</span></nav><p className="article-byline">By <a href="/about">Hooker Hill Studios</a> · Published <time dateTime={article.published}>September 26, 2026</time> · {Math.ceil(article.body.split(/\s+/).length/220)} min read</p><MarkdownContent content={article.body}/><aside className="article-next"><h2>Put the boundaries into practice.</h2><p>Start with repository authorization, an enrolled runner, and explicit verification policy.</p><a className="primary" href="/docs">Read the setup guide</a><a href="/blog">More field notes →</a></aside></article></PublicLayout>;
  const page=pages.find(item=>item.path===path);
  if(!page)return <PublicLayout><PublicHero eyebrow="404 / Page not found" heading="This path does not lead to a run." intro="The page may have moved, or the address may be incomplete."/><section className="public-section"><a className="primary" href="/">Return home</a> <a href="/docs">Read the setup guide</a></section></PublicLayout>;
  return <PublicLayout><PublicHero eyebrow={page.eyebrow} heading={page.heading} intro={page.intro}/>{path==='/blog'?<section className="public-section"><p className="editorial-note">Engineering guidance, published with real dates. These articles describe design practices, not measured performance claims.</p><ArticleCards/></section>:<section className="public-section page-sections">{page.sections.map((section,index)=><section key={section.heading}><span className="section-number">{String(index+1).padStart(2,'0')}</span><div><h2>{section.heading}</h2><p>{section.body}</p>{section.points&&<ul>{section.points.map(point=><li key={point}>{point}</li>)}</ul>}</div></section>)}<div className="public-cta"><h2>See the system. Read the evidence.</h2><p>Explore the implementation and current release checklist before connecting your first repository.</p><div className="landing-actions"><a className="primary" href="/oauth2/authorization/github">Open the console</a><a className="secondary" href={REPOSITORY}>View on GitHub</a></div></div></section>}</PublicLayout>;
}
