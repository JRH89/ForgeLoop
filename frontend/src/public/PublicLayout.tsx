import type { ReactNode } from 'react';
const favicon='/icons/icon-192.png';
import { REPOSITORY } from './content';
import { heroStyle } from './heroes';
import { BookOpen, CircleHelp, FileText, Info, Layers, LifeBuoy, Menu, Shield, X } from 'lucide-react';

const links = [['Features','/features'],['How it works','/how-it-works'],['About','/about'],['Blog','/blog'],['Docs','/docs']];
const mobileIcons = [Layers, CircleHelp, Info, FileText, BookOpen, Shield, LifeBuoy];
export function PublicHeader() {
  return <header className="landing-nav public-nav">
    <a className="brand" href="/" aria-label="ForgeLoop home"><img src={favicon} width="30" height="30" alt=""/><strong>ForgeLoop</strong></a>
    <nav aria-label="Main navigation">{links.map(([label,path])=><a key={path} href={path}>{label}</a>)}</nav>
    <a className="primary" href="/oauth2/authorization/github">Sign in with GitHub</a>
    <button className="menu-toggle" type="button" aria-label="Open navigation" aria-expanded="false" aria-controls="mobile-menu" hidden><Menu className="menu-open-icon" size={22} aria-hidden="true"/><X className="menu-close-icon" size={22} aria-hidden="true"/></button>
    <div id="mobile-menu" className="mobile-dropdown" inert aria-hidden="true">
      <nav aria-label="Mobile navigation">{[...links,['Security','/security'],['Contact','/contact']].map(([label,path],index)=>{const Icon=mobileIcons[index];return <a key={path} href={path}><span className="menu-link-icon"><Icon size={20} aria-hidden="true"/></span>{label}</a>;})}</nav>
      <a className="mobile-signin" href="/oauth2/authorization/github">Sign in with GitHub</a>
    </div>
    <noscript><nav aria-label="Mobile navigation">{links.map(([label,path])=><a key={path} href={path}>{label}</a>)}</nav></noscript>
  </header>;
}
export function PublicFooter() {
  const groups=[['Product',[['Features','/features'],['How it works','/how-it-works'],['Security','/security'],['Open the console','/oauth2/authorization/github']]],['Resources',[['Getting started','/docs'],['Field notes','/blog'],['User guide','/app'],['Release progress',`${REPOSITORY}#readme`]]],['Project',[['About','/about'],['Contact & support','/contact'],['Track support tickets','/support'],['GitHub',REPOSITORY],['Issue tracker',`${REPOSITORY}/issues`]]]] as const;
  return <footer className="public-footer"><div className="footer-intro"><a className="brand" href="/"><img src={favicon} width="30" height="30" alt=""/><strong>ForgeLoop</strong></a><p>Evidence-backed software delivery.</p><p>Built by Hooker Hill Studios.</p></div>{groups.map(([title,items])=><nav key={title} aria-label={`${title} footer`}><h2>{title}</h2>{items.map(([label,url])=><a href={url} key={label}>{label}</a>)}</nav>)}<p className="footer-note">© 2026 Hooker Hill Studios. Desktop installers are development previews; signed public distribution remains a release milestone.</p></footer>;
}
export function PublicLayout({children}:{children:ReactNode}) {
  return <div className="landing public-site"><a className="skip-link" href="#main-content">Skip to content</a><PublicHeader/><main id="main-content">{children}</main><PublicFooter/></div>;
}
export function PublicHero({path,eyebrow,heading,intro}:{path:string;eyebrow:string;heading:string;intro:string}) {
  return <section className="public-hero" style={heroStyle(path)}><div><p className="eyebrow">{eyebrow}</p><h1>{heading}</h1><p>{intro}</p></div></section>;
}
