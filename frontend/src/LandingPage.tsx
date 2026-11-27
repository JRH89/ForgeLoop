import { useEffect, useState } from 'react';
import { GitBranch, RefreshCw, ShieldCheck, Server } from 'lucide-react';
import { PublicLayout } from './public/PublicLayout';
import { ArticleCards } from './public/ArticleCards';
import { heroStyle } from './public/heroes';
import './public/public.css';

const capabilities = [
  ["Connect any GitHub repo", "Install the least-privilege GitHub App and choose which issues or labels enter the queue—no repo migration required.", GitBranch],
  ["Plan, code, verify, repair", "Each issue runs through a bounded loop: plan the change, implement in an isolated worktree, run your test suite, and auto-repair failures.", RefreshCw],
  ["Proof before review", "Every run produces checksummed logs, test results, acceptance-criteria coverage, and cost telemetry tied to one exact commit.", ShieldCheck],
  ["Runs on your infrastructure", "Source code, shell commands, containers, and model API keys stay on runners you host—never in the control plane.", Server],
] as const;

/** Public product surface; it never loads tenant data before authentication. */
export default function LandingPage() {
  const [loginFailed,setLoginFailed] = useState(false);
  useEffect(()=>setLoginFailed(new URLSearchParams(window.location.search).get('login')==='failed'),[]);
  return <PublicLayout>
    {loginFailed&&<p className="landing-alert" role="alert">GitHub sign-in could not be completed. Please try signing in again. If it still fails, contact support.</p>}
    <section className="landing-hero" style={heroStyle('/')}>
      <p className="eyebrow">Autonomous software delivery</p>
      <h1>Turn GitHub issues into<br/><em>evidence-backed pull requests.</em></h1>
      <p>Assign an issue and let ForgeLoop handle the loop. It plans the change, runs coding agents on your own runners, verifies tests and acceptance criteria, repairs failures, and opens a review-ready PR with full proof.</p>
      <div className="landing-actions">
        <a className="primary" href="/oauth2/authorization/github">Open the console</a>
        <a className="secondary" href="/demo">Watch the demo run</a>
        <a className="secondary" href="#how">See the delivery loop</a>
      </div>
    </section>
    <section className="landing-section" id="how">
      <p className="eyebrow">How it works</p>
      <h2>Agents write code. ForgeLoop proves it is ready to merge.</h2>
      <p className="section-lead">Instead of handing an agent an open terminal, ForgeLoop coordinates a repeatable delivery pipeline around your repository and existing checks.</p>
      <div className="landing-grid">{capabilities.map(([title,body,Icon])=><article key={title}><span className="capability-icon" aria-hidden="true"><Icon size={24} strokeWidth={1.8}/></span><h3>{title}</h3><p>{body}</p></article>)}</div>
    </section>
    <section className="landing-section landing-proof" id="security">
      <div>
        <p className="eyebrow">Designed for real repositories</p>
        <h2>Explicit boundaries at every layer.</h2>
        <p>ForgeLoop separates coordination from execution. The control plane tracks tasks and evidence, while your enrolled runners handle source code, secrets, and verification commands inside isolated git worktrees.</p>
      </div>
      <ul>
        <li>GitHub OAuth identity and tenant-scoped role membership</li>
        <li>Zero repository source code stored in the control plane</li>
        <li>Runner-local API keys, shell tools, and MCP servers</li>
        <li>Human PR approval by default, with opt-in guarded auto-merge</li>
      </ul>
    </section>
    <section className="public-section">
      <p className="eyebrow">Three steps to your first PR</p>
      <h2>A clear boundary between connecting and running.</h2>
      <div className="public-cards">
        <article>
          <span className="step-label">Step 01</span>
          <h3>Authorize your repositories</h3>
          <p>Sign in with GitHub, install the GitHub App on selected repositories, and configure intake rules to decide which issues become eligible for automated runs.</p>
          <a href="/how-it-works">Follow the workflow →</a>
        </article>
        <article>
          <span className="step-label">Step 02</span>
          <h3>Pair your local runner</h3>
          <p>Install the desktop or CLI runner, store model provider keys locally on your machine, and verify prerequisites. Setup checks never invoke a model or incur charges.</p>
          <a href="/docs">Explore runner setup →</a>
        </article>
        <article>
          <span className="step-label">Step 03</span>
          <h3>Review the evidence, not just the diff</h3>
          <p>Start the runner to claim eligible issues. Inspect the diff, verification gate results, and acceptance coverage before approving—or enable guarded auto-merge when ready.</p>
          <a href="/features">Explore the controls →</a>
        </article>
      </div>
    </section>
    <section className="public-section public-faq">
      <p className="eyebrow">Before your first run</p>
      <h2>Questions worth asking.</h2>
      {[
        ['Can I connect my own repositories?','Yes. Connect any authorized GitHub repository with its own tech stack and verification commands. ForgeLoop runs all build and test work on your enrolled infrastructure.'],
        ['Do I need to supply a model API key?','Yes. Configure a supported model provider directly on your runner. Model billing is separate from GitHub authentication, and only active runs incur provider usage.'],
        ['Does connecting a runner start paid work?','No. Browser pairing, saving settings, local key checks, and runner heartbeats never call a model. Paid work only begins when you start the runner and it claims an eligible issue.'],
        ['Is the desktop installer a signed public release?','Not yet. Windows, macOS, and Linux desktop installers are available as unsigned development previews on GitHub Releases while publisher signing is finalized.']
      ].map(([question,answer])=><details key={question}><summary>{question}</summary><p>{answer}</p></details>)}
    </section>
    <section className="public-section"><p className="eyebrow">Field notes</p><h2>The engineering behind dependable agent workflows.</h2><ArticleCards limit={4}/><a className="text-link" href="/blog">Read all field notes →</a></section>
  </PublicLayout>;
}
