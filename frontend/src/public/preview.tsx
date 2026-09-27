import { createRoot } from 'react-dom/client';
import PublicSite from './PublicSite';
const root=document.getElementById('root');
if(root)createRoot(root).render(<PublicSite path={window.location.pathname}/>);
