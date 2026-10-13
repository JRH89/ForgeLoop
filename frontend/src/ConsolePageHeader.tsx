import type { ReactNode } from 'react';
import './console-page-header.css';

/** One typography and spacing contract for every console tab. */
export default function ConsolePageHeader({ eyebrow, title, description, actions }: {
  eyebrow: string; title: string; description: string; actions?: ReactNode;
}) {
  return <section className="console-page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="console-page-description">{description}</p></div>{actions && <div className="console-page-actions">{actions}</div>}</section>;
}
