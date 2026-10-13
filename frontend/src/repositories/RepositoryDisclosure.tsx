import { useState, type ReactNode } from 'react';

/** Native disclosure keeps keyboard behavior; only a UI preference is persisted. */
export default function RepositoryDisclosure({ storageKey, heading, children }: {
  storageKey: string; heading: ReactNode; children: ReactNode;
}) {
  const [open, setOpen] = useState(() => {
    try { return localStorage.getItem(storageKey) !== 'closed'; } catch { return true; }
  });
  return <details className="repository-card repository-disclosure" open={open} onToggle={event => {
    const expanded = event.currentTarget.open;
    setOpen(expanded);
    try { localStorage.setItem(storageKey, expanded ? 'open' : 'closed'); } catch { /* Blocked storage must not prevent toggling. */ }
  }}>
    <summary className="repository-card-heading">{heading}</summary>
    <div className="repository-disclosure-content">{children}</div>
  </details>;
}
