/** Enhance static HTML without loading React or the authenticated console. */
export function initializeNavigation() {
  const toggle = document.querySelector<HTMLButtonElement>('.menu-toggle');
  const drawer = document.querySelector<HTMLDialogElement>('.mobile-drawer');
  if (!toggle || !drawer || toggle.dataset.ready) return;
  toggle.dataset.ready = 'true';
  toggle.hidden = false;
  let closing = false;
  let previousOverflow = '';
  const close = () => {
    if (!drawer.open || closing) return;
    closing = true;
    drawer.classList.remove('is-open');
    // Keep the native modal/focus trap active until the exit transition completes.
    window.setTimeout(() => {
      drawer.close();
      closing = false;
    }, matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 260);
  };
  toggle.addEventListener('click', () => {
    if (drawer.open) return;
    previousOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = 'hidden';
    drawer.showModal();
    toggle.setAttribute('aria-expanded', 'true');
    requestAnimationFrame(() => requestAnimationFrame(() => drawer.classList.add('is-open')));
  });
  drawer.querySelector('.menu-close')?.addEventListener('click', close);
  drawer.addEventListener('cancel', event => { event.preventDefault(); close(); });
  drawer.addEventListener('keydown', event => {
    if (event.key !== 'Tab') return;
    const items = Array.from(drawer.querySelectorAll<HTMLElement>('button, a[href]'));
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus();
    }
  });
  drawer.addEventListener('click', event => { if (event.target === drawer) close(); });
  drawer.addEventListener('close', () => {
    drawer.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
    document.documentElement.style.overflow = previousOverflow;
    toggle.focus();
  });
  // Do not leave an invisible modal trapping focus when rotating to desktop width.
  matchMedia('(min-width: 1001px)').addEventListener('change', event => { if (event.matches) close(); });
}
