import { useState } from 'react';
import { X } from 'lucide-react';
import type { OperatorSession } from './api';

/** Persist only the preference, scoped to the signed-in identity and workspace. */
export default function SetupBanner({ operator, onOpen }: { operator: OperatorSession; onOpen: () => void }) {
  const key = `forgeloop:setup-dismissed:${encodeURIComponent(operator.subject)}:${encodeURIComponent(operator.organizationId)}`;
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem(key) === 'true'; } catch { return false; }
  });
  if (dismissed) return null;
  return <div className="setup-entry"><span>Setting up a repository or diagnosing waiting work?</span><div className="setup-entry-actions"><button onClick={onOpen}>Open setup checklist</button><button aria-label="Dismiss getting started banner permanently" title="Don't show again in this browser" onClick={() => {
    try { localStorage.setItem(key, 'true'); } catch { /* Storage may be blocked; dismissal still works for this session. */ }
    setDismissed(true);
  }}><X size={16} aria-hidden="true"/></button></div></div>;
}
