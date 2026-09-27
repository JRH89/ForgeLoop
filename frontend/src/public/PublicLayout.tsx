import type { ReactNode } from 'react';
const favicon='/icons/icon-192.png';
import { REPOSITORY } from './content';

const links = [['Features','/features'],['How it works','/how-it-works'],['About','/about'],['Blog','/blog'],['Docs','/docs']];
export function PublicHeader() {
  return <header className="landing-nav public-nav"><a className="brand" href="/" aria-label="ForgeLoop home"><img src={favicon} width="30" height="30" alt=""/><strong>ForgeLoop</strong></a><nav aria-label="Main navigation">{links.map(([label,path])=><a key={path} href={path}>{label}</a>)}</nav><a className="primary" href="/oauth2/authorization/github">Sign in with GitHub</a><details className="mobile-navigation"><summary>Menu</summary><nav aria-label="Mobile navigation">{links.map(([label,path])=><a key={path} href={path}>{label}</a>)}<a href="/security">Security</a></nav></details></header>;
}
export function PublicFooter() {
  const groups=[['Product',[['Features','/features'],['How it works','/how-it-works'],['Security','/security'],['Open the console','/oauth2/authorization/github']]],['Resources',[['Getting started','/docs'],['Field notes','/blog'],['User guide','/app'],['Release progress',`${REPOSITORY}#readme`]]],['Project',[['About','/about'],['Contact & support','/contact'],['GitHub',REPOSITORY],['Issue tracker',`${REPOSITORY}/issues`]]]] as const;
  return <footer className="public-footer"><div className="footer-intro"><a className="brand" href="/"><img src={favicon} width="30" height="30" alt=""/><strong>ForgeLoop</strong></a><p>Evidence-backed software delivery.</p><p>Built by Hooker Hill Studios.</p></div>{groups.map(([title,items])=><nav key={title} aria-label={`${title} footer`}><h2>{title}</h2>{items.map(([label,url])=><a href={url} key={label}>{label}</a>)}</nav>)}<p className="footer-note">© 2026 Hooker Hill Studios. Desktop installers are development previews; signed public distribution remains a release milestone.</p></footer>;
}
export function PublicLayout({children}:{children:ReactNode}) {
  return <div className="landing public-site"><a className="skip-link" href="#main-content">Skip to content</a><PublicHeader/><main id="main-content">{children}</main><PublicFooter/></div>;
}
export function PublicHero({eyebrow,heading,intro}:{eyebrow:string;heading:string;intro:string}) {
  return <section className="public-hero"><div><p className="eyebrow">{eyebrow}</p><h1>{heading}</h1><p>{intro}</p></div></section>;
}
