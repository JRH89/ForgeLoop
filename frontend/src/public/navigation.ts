/** Enhance static HTML with the console's non-modal dropdown interaction. */
export function initializeNavigation() {
  const toggle = document.querySelector<HTMLButtonElement>('.menu-toggle');
  const panel = document.querySelector<HTMLElement>('.mobile-dropdown');
  if (!toggle || !panel || toggle.dataset.ready) return;
  toggle.dataset.ready = 'true';
  toggle.hidden = false;
  let open = false;
  const setOpen = (value: boolean, restoreFocus = false) => {
    open = value;
    panel.classList.toggle('is-open', open);
    panel.inert = !open;
    panel.setAttribute('aria-hidden', String(!open));
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    if (restoreFocus) toggle.focus();
  };
  toggle.addEventListener('click', () => setOpen(!open));
  document.addEventListener('keydown', event => {
    if (open && event.key === 'Escape') setOpen(false, true);
  });
  document.addEventListener('pointerdown', event => {
    if (open && event.target instanceof Node && !panel.contains(event.target) && !toggle.contains(event.target)) setOpen(false);
  });
  // Normal tabbing can leave this non-modal panel; no scroll lock or focus trap.
  document.addEventListener('focusin', event => {
    if (open && event.target instanceof Node && !panel.contains(event.target) && !toggle.contains(event.target)) setOpen(false);
  });
  panel.addEventListener('click', event => {
    if (event.target instanceof Element && event.target.closest('a')) setOpen(false);
  });
  for (const link of panel.querySelectorAll<HTMLAnchorElement>('nav a')) {
    if (link.pathname === window.location.pathname) link.setAttribute('aria-current', 'page');
  }
  matchMedia('(min-width: 1001px)').addEventListener('change', event => {
    if (event.matches) setOpen(false);
  });
}
