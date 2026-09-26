import favicon from "./assets/favicon.png";

const capabilities = [
  ["Repository agnostic", "Connect any GitHub repository through a least-privilege GitHub App installation."],
  ["Closed-loop delivery", "Plan, implement, integrate, verify, repair, independently review, and deliver one exact commit."],
  ["Evidence, not vibes", "Every gate produces checksummed evidence, criterion coverage, provider telemetry, and an audit trail."],
  ["Your runners", "Source code, shell commands, containers, and browser tests stay on enrolled infrastructure you control."],
];

/** Public product surface; it never loads tenant data before authentication. */
export default function LandingPage() {
  const loginFailed = new URLSearchParams(window.location.search).get("login") === "failed";
  return <main className="landing">
    <header className="landing-nav"><a className="brand" href="/"><img src={favicon} alt=""/><strong>ForgeLoop</strong></a><div><a href="#how">How it works</a><a href="#security">Security</a><a className="primary" href="/oauth2/authorization/github">Sign in with GitHub</a></div></header>
    {loginFailed&&<p className="landing-alert" role="alert">GitHub sign-in could not be completed. Confirm that your account has been invited to this ForgeLoop organization.</p>}
    <section className="landing-hero"><p className="eyebrow">Autonomous software delivery</p><h1>Turn GitHub issues into<br/><em>evidence-backed pull requests.</em></h1><p>ForgeLoop is the control plane around coding agents: it delegates bounded work, runs it on your infrastructure, repairs failures, proves acceptance criteria, and delivers the reviewed commit.</p><div className="landing-actions"><a className="primary" href="/oauth2/authorization/github">Open the console</a><a className="secondary" href="#how">See the delivery loop</a></div><div className="landing-terminal"><span>ISSUE RECEIVED</span><span>PLAN VALIDATED</span><span>4 TASKS EXECUTED</span><span>6/6 GATES PASSED</span><b>VERIFIED PR READY</b></div></section>
    <section className="landing-section" id="how"><p className="eyebrow">The delivery loop</p><h2>Agents write code. ForgeLoop establishes confidence.</h2><div className="landing-grid">{capabilities.map(([title,body],index)=><article key={title}><span>0{index+1}</span><h3>{title}</h3><p>{body}</p></article>)}</div></section>
    <section className="landing-section landing-proof" id="security"><div><p className="eyebrow">Designed for real repositories</p><h2>Explicit boundaries at every layer.</h2><p>Signed GitHub webhooks, short-lived installation credentials, tenant-scoped membership, task leases, isolated worktrees, policy-selected verification, immutable evidence, bounded retries, and guarded expected-SHA delivery.</p></div><ul><li>GitHub identity and persisted role membership</li><li>No repository source stored in the control plane</li><li>Runner-local secrets and MCP processes</li><li>Human approval and opt-in guarded auto-merge</li></ul></section>
    <footer><a className="brand" href="/"><img src={favicon} alt=""/><strong>ForgeLoop</strong></a><span>Evidence-backed software delivery.</span></footer>
  </main>;
}
