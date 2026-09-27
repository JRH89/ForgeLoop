import { renderToString } from 'react-dom/server';
import PublicSite from './PublicSite';
export { metadata, publicPaths } from './seo';
export { articles } from './articles';
export { SITE } from './content';
export function render(path:string) { return renderToString(<PublicSite path={path}/>); }
