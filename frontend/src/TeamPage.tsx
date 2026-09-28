import { useEffect, useState, type FormEvent } from 'react';
import { inviteGithubUser, loadOrganizationMembers, revokeOrganizationMember, type OperatorSession, type OrganizationMember } from './api';
import './team.css';

/** Administrator-only controls; every operation is also authorized by the server. */
export default function TeamPage({operator}: {operator: OperatorSession}) {
  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [login, setLogin] = useState('');
  const [role, setRole] = useState<OperatorSession['role']>('OPERATOR');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => { void loadOrganizationMembers(operator.organizationId).then(setMembers).catch(reason => setError(String(reason))); }, [operator.organizationId]);
  async function invite(event: FormEvent) {
    event.preventDefault(); setError(''); setMessage(''); setBusy(true);
    try {
      const member = await inviteGithubUser(operator.organizationId, login.trim(), role);
      setMembers(current => [...current, member].sort((a,b) => (a.githubLogin ?? a.subject).localeCompare(b.githubLogin ?? b.subject)));
      setLogin(''); setMessage(`Invitation ready for @${member.githubLogin}. They can now sign in with GitHub.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
    finally { setBusy(false); }
  }
  async function revoke(member: OrganizationMember) {
    if (!window.confirm(`Remove ${member.githubLogin ? `@${member.githubLogin}` : member.subject} from this organization? Their next request will be denied.`)) return;
    setError(''); setMessage(''); setBusy(true);
    try { await revokeOrganizationMember(operator.organizationId, member.id); setMembers(current => current.filter(item => item.id !== member.id)); setMessage('Access removed.'); }
    catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
    finally { setBusy(false); }
  }
  return <section className="team-page">
    <div className="page-heading"><div><span className="eyebrow">ORGANIZATION ACCESS</span><h1>Team</h1><p>Invite GitHub users to {operator.organizationId}. Only administrators can manage access.</p></div></div>
    <div className="team-card"><h2>Invite a person</h2><p>Enter their GitHub username. ForgeLoop resolves it to GitHub's permanent account ID before granting access. No email is sent; share your ForgeLoop URL with them.</p>
      <form onSubmit={event => void invite(event)} className="team-invite-form"><label>GitHub username<input value={login} onChange={event => setLogin(event.target.value)} required autoComplete="off" placeholder="octocat" /></label><label>Role<select value={role} onChange={event => setRole(event.target.value as OperatorSession['role'])}><option value="VIEWER">Viewer — read only</option><option value="OPERATOR">Operator — manage runs</option><option value="ADMIN">Administrator — manage organization</option></select></label><button disabled={busy || !login.trim()} type="submit">Invite GitHub user</button></form>
    </div>
    {error && <p role="alert" className="team-error">{error}</p>}{message && <p role="status" className="team-success">{message}</p>}
    <div className="team-card"><h2>Members and invitations</h2><ul className="team-members">{members.map(member => <li key={member.id}><div><strong>{member.githubLogin ? `@${member.githubLogin}` : member.subject}</strong><small>{member.role.toLowerCase()} · {member.accepted ? 'Signed in' : 'Pending first sign-in'}</small></div><button disabled={busy || member.subject === operator.subject} onClick={() => void revoke(member)}>Remove</button></li>)}</ul>{members.length === 0 && <p>No members found.</p>}</div>
  </section>;
}
