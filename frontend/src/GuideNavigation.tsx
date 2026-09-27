import { useState } from 'react';
import { BookOpen, ChevronDown } from 'lucide-react';

export const guideSections = [
  ['quick-start', 'Quick start'],
  ['runner-setup', 'Runner setup & API keys'],
  ['repositories', 'Repositories'],
  ['runs', 'Runs'],
  ['policy', 'Settings'],
  ['delivery', 'Delivery'],
  ['troubleshooting', 'Troubleshooting'],
] as const;

/** Sidebar disclosure stays open on mobile until a section is selected. */
export default function GuideNavigation({ active, onSelect }: { active: boolean; onSelect: (id: string) => void }) {
  const [expanded, setExpanded] = useState(false);
  return <div className="guide-navigation">
    <button className={active ? 'active' : ''} aria-expanded={expanded} aria-controls="guide-submenu"
      onClick={event => { event.stopPropagation(); setExpanded(value => !value); }}>
      <span className="nav-icon" aria-hidden="true"><BookOpen size={17} strokeWidth={1.9}/></span>
      User guide <ChevronDown className="guide-chevron" size={16} aria-hidden="true"/>
    </button>
    <div id="guide-submenu" className="guide-submenu" hidden={!expanded}>
      {guideSections.map(([id, label]) => <button key={id} onClick={() => onSelect(id)}>{label}</button>)}
    </div>
  </div>;
}
