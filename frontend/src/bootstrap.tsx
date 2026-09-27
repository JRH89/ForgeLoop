import { createRoot, hydrateRoot } from 'react-dom/client';
import PublicSite from './public/PublicSite';
import './styles.css';
import './public/public.css';

// Public visitors do not need operator-console code or tenant API requests.
if (/^\/app(?:\/|$)/.test(window.location.pathname)) {
  void import('./main');
} else {
  const root=document.getElementById('root');
  if(!root)throw new Error('ForgeLoop root element is missing');
  const page=<PublicSite path={window.location.pathname}/>;
  if(root.hasChildNodes())hydrateRoot(root,page);else createRoot(root).render(page);
}
