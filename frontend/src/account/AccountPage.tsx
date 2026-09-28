import { ArrowUpRight, BarChart3, Settings2, ShieldCheck } from 'lucide-react';
import type { OrganizationMember, OperatorSession } from '../api';
import SupportApp from '../support/SupportApp';
import './account.css';

type AccountPageProps = {
  operator: OperatorSession;
  workspaces: OrganizationMember[];
  onUsage: () => void;
  onWorkspaceSettings: () => void;
};

/** Brings identity, workspace access, and the existing private ticket center together. */
export default function AccountPage({ operator, workspaces, onUsage, onWorkspaceSettings }: AccountPageProps) {
  const activeWorkspace = workspaces.find(item => item.organizationId === operator.organizationId);

  return <section className="account-page">
    <div className="page-title"><div><p className="eyebrow">Profile &amp; help</p><h1>Account</h1><p>Review your signed-in identity, workspace access, and support requests.</p></div><a className="logout account-signout" href="/logout">Sign out</a></div>

    <div className="account-overview" aria-label="Account overview">
      <article><span>Signed-in GitHub identity</span><strong>{operator.subject}</strong><small>Your identity is managed by GitHub.</small></article>
      <article><span>Active workspace</span><strong>{activeWorkspace?.organizationName ?? operator.organizationId}</strong><small>{operator.role.toLowerCase()} access</small></article>
      <article><span>Workspace access</span><strong>{workspaces.length} {workspaces.length === 1 ? 'workspace' : 'workspaces'}</strong><small>Switch workspaces from the dashboard header.</small></article>
    </div>

    <section className="panel account-shortcuts" aria-labelledby="account-settings-heading">
      <div><p className="eyebrow">Workspace tools</p><h2 id="account-settings-heading">Settings &amp; activity</h2><p>Usage details and organization-level execution controls live in their dedicated workspace pages.</p></div>
      <div className="account-shortcut-actions">
        <button type="button" className="secondary" onClick={onUsage}><BarChart3 aria-hidden="true" size={17}/> Usage &amp; costs</button>
        <button type="button" className="secondary" onClick={onWorkspaceSettings}><Settings2 aria-hidden="true" size={17}/> Workspace settings</button>
        <a href="/docs"><ShieldCheck aria-hidden="true" size={17}/> Runner &amp; security guide <ArrowUpRight aria-hidden="true" size={15}/></a>
      </div>
    </section>

    <section className="panel account-support" aria-labelledby="account-support-heading">
      <div className="account-support-heading"><div><p className="eyebrow">Private support</p><h2 id="account-support-heading">Your support tickets</h2><p>View replies, search your requests, and follow ticket status.</p></div><a className="primary" href="/contact#support">New support request</a></div>
      <SupportApp accountMode />
    </section>
  </section>;
}
