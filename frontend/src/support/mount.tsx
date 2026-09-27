import {createRoot} from 'react-dom/client';
import SupportApp from './SupportApp';

/** Mount only the interactive island; surrounding marketing HTML stays prerendered. */
export function mountSupport(){
  const element=document.getElementById('support-root');
  if(element&&!element.dataset.mounted){element.dataset.mounted='true';createRoot(element).render(<SupportApp contact={element.dataset.mode==='contact'}/>);}
}
mountSupport();
