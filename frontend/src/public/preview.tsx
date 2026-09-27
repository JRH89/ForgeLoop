import { createRoot } from 'react-dom/client';
import { useEffect } from 'react';
import { initializeNavigation } from './navigation';
import PublicSite from './PublicSite';
function Preview() {
  useEffect(initializeNavigation, []);
  useEffect(()=>{if(['/contact','/support'].includes(window.location.pathname))void import('../support/mount').then(module=>module.mountSupport());},[]);
  return <PublicSite path={window.location.pathname}/>;
}
const root=document.getElementById('root');
if(root)createRoot(root).render(<Preview/>);
