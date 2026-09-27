import './styles.css';
import './public/public.css';
import { initializeNavigation } from './public/navigation';

// Public visitors do not need operator-console code or tenant API requests.
if (/^\/app(?:\/|$)/.test(window.location.pathname)) {
  void import('./main');
} else if(import.meta.env.DEV) {
  // Vite development does not have prerendered HTML; production needs no React runtime here.
  void import('./public/preview');
} else if(new URLSearchParams(window.location.search).get('login')==='failed') {
  const message=document.createElement('p');message.className='landing-alert';message.setAttribute('role','alert');
  message.textContent='GitHub sign-in could not be completed. Confirm that your account has been invited to this ForgeLoop organization.';
  document.getElementById('main-content')?.prepend(message);
}

initializeNavigation();
