import { useEffect, useState } from 'react';
import { PublicLayout } from './public/PublicLayout';
import { articles } from './public/articles';
import './public/public.css';

const capabilities = [
  ["Repository agnostic", "Connect any GitHub repository through a least-privilege GitHub App installation."],
  ["Closed-loop delivery", "Plan, implement, integrate, verify, repair, independently review, and deliver one exact commit."],
  ["Evidence, not vibes", "Every gate produces checksummed evidence, criterion coverage, provider telemetry, and an audit trail."],
  ["Your runners", "Source code, shell commands, containers, and browser tests stay on enrolled infrastructure you control."],
];

/** Public product surface; it never loads tenant data before authentication. */
export default function LandingPage() {
  const [loginFailed,setLoginFailed] = useState(false);
  useEffect(()=>setLoginFailed(new URLSearchParams(window.location.search).get('login')==='failed'),[]);
  return <PublicLayout>
    {loginFailed&&<p className="landing-alert" role="alert">GitHub sign-in could not be completed. Confirm that your account has been invited to this ForgeLoop organization.</p>}
    <section className="landing-hero"><p className="eyebrow">Autonomous software delivery</p><h1>Turn GitHub issues into<br/><em>evidence-backed pull requests.</em></h1><p>ForgeLoop is the control plane around coding agents: it delegates bounded work, runs it on your infrastructure, repairs failures, proves acceptance criteria, and delivers the reviewed commit.</p><div className="landing-actions"><a className="primary" href="/oauth2/authorization/github">Open the console</a><a className="secondary" href="#how">See the delivery loop</a></div><div className="landing-terminal"><span>ISSUE RECEIVED</span><span>PLAN VALIDATED</span><span>4 TASKS EXECUTED</span><span>6/6 GATES PASSED</span><b>VERIFIED PR READY</b></div></section>
    <section className="landing-section" id="how"><p className="eyebrow">The delivery loop</p><h2>Agents write code. ForgeLoop establishes confidence.</h2><div className="landing-grid">{capabilities.map(([title,body],index)=><article key={title}><span>0{index+1}</span><h3>{title}</h3><p>{body}</p></article>)}</div></section>
    <section className="landing-section landing-proof" id="security"><div><p className="eyebrow">Designed for real repositories</p><h2>Explicit boundaries at every layer.</h2><p>Signed GitHub webhooks, short-lived installation credentials, tenant-scoped membership, task leases, isolated worktrees, policy-selected verification, immutable evidence, bounded retries, and guarded expected-SHA delivery.</p></div><ul><li>GitHub identity and persisted role membership</li><li>No repository source stored in the control plane</li><li>Runner-local secrets and MCP processes</li><li>Human approval and opt-in guarded auto-merge</li></ul></section>
    <section className="public-section"><p className="eyebrow">From setup to deliberate execution</p><h2>A clear boundary between connecting and running.</h2><div className="public-cards"><article><h3>Authorize your repositories</h3><p>GitHub sign-in identifies you. GitHub App installation grants repository access. Intake policy decides which issues become eligible for work.</p><a href="/how-it-works">Follow the workflow →</a></article><article><h3>Keep execution on your runner</h3><p>Save provider keys locally, verify prerequisites, and explicitly start processing. Setup checks do not call a model; work execution can incur charges.</p><a href="/docs">Explore runner setup →</a></article><article><h3>Review the result, not just the summary</h3><p>Inspect the diff, acceptance coverage, gate results, and commit identity. Keep human approval on until your evidence supports a narrower auto-merge policy.</p><a href="/features">Explore the controls →</a></article></div></section>
    <section className="public-section public-faq"><p className="eyebrow">Before your first run</p><h2>Questions worth asking.</h2>{[
      ['Does ForgeLoop work only with Ticketly?','No. Ticketly is a separate test repository. ForgeLoop is designed to connect authorized GitHub repositories with their own stacks and verification policies.'],
      ['Do I need to supply a model API key?','Yes, configure a supported provider on your runner. Provider usage is separate from GitHub authentication. Actual coding work can incur provider charges.'],
      ['Does connecting a runner start paid work?','No. Browser pairing, saving settings, local key checks, and heartbeat checks do not invoke a model. Start runner can claim eligible work; it is not a dry run.'],
      ['Is the desktop installer a signed public release?','Not yet. Windows, macOS, and Linux native packages have development-preview CI coverage. Signing, public distribution, and remaining live validation have separate release gates.']
    ].map(([question,answer])=><details key={question}><summary>{question}</summary><p>{answer}</p></details>)}</section>
    <section className="public-section"><p className="eyebrow">Field notes</p><h2>The engineering behind dependable agent workflows.</h2><div className="public-cards">{articles.slice(0,3).map(article=><article key={article.slug}><h3><a href={`/blog/${article.slug}`}>{article.title}</a></h3><p>{article.description}</p></article>)}</div><a className="text-link" href="/blog">Read all field notes →</a></section>
  </PublicLayout>;
}
