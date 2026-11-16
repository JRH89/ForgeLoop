import type { CSSProperties } from 'react';
import { DEMO_REPOSITORY, DEMO_RUN, demoStages } from './demo';
import { PublicHero } from './PublicLayout';

const mergedAt = new Date(DEMO_RUN.mergedAt).toLocaleString('en-US', { timeZone: 'UTC', dateStyle: 'long', timeStyle: 'short' }) + ' UTC';

/**
 * A prerendered replay of a real unattended run. The timeline animates in pure CSS
 * (no script, so it works under the strict CSP and without JavaScript) and shows
 * every step immediately when the visitor prefers reduced motion.
 */
export default function DemoPage({ path, eyebrow, heading, intro }: { path: string; eyebrow: string; heading: string; intro: string }) {
  return <>
    <PublicHero path={path} eyebrow={eyebrow} heading={heading} intro={intro}/>
    <section className="public-section demo">
      <div className="demo-summary" aria-label="Run outcome">
        <div><span>Source issue</span><a href={DEMO_RUN.issue.url}>project-hub #{DEMO_RUN.issue.number}</a></div>
        <div><span>Pull request</span><a href={DEMO_RUN.pullRequest.url}>#{DEMO_RUN.pullRequest.number} · merged</a></div>
        <div><span>Human approvals</span><strong>0</strong></div>
        <div><span>Known provider cost</span><strong>${DEMO_RUN.knownCostUsd.toFixed(2)} of ${DEMO_RUN.budgetUsd} cap</strong></div>
      </div>
      <h2>Replay: {DEMO_RUN.issue.title}</h2>
      <ol className="demo-timeline">
        {demoStages.map((step, index) => <li key={step.stage} style={{ '--step': index } as CSSProperties}>
          <span className="demo-marker" aria-hidden="true">{index + 1}</span>
          <div>
            <p className="eyebrow">{step.stage}</p>
            <h3>{step.title}</h3>
            <p>{step.detail}</p>
            <code>{step.evidence}</code>
          </div>
        </li>)}
      </ol>
      <div className="demo-terminal" role="group" aria-label="Recorded verification gate">
        <p><span className="demo-prompt">$</span> {DEMO_RUN.gateCommand}</p>
        <p className="demo-pass">✓ unit gate PASSED</p>
        <p>merged {DEMO_RUN.mergeCommit.slice(0, 7)} · {mergedAt}</p>
      </div>
      <div className="page-sections">
        <section><span className="section-number" aria-hidden="true">—</span><div><h2>What this demo does not claim</h2><p>The cost figure is recorded provider telemetry, not a verified total; some telemetry was unavailable. Two earlier attempts on the same repository blocked safely and are documented as debugging history rather than hidden. Unattended merge is opt-in: by default, a human approves before anything reaches GitHub.</p></div></section>
      </div>
      <div className="public-cta"><h2>Inspect the evidence yourself.</h2><p>The issue, the bot-authored pull request, and the merge commit are public. The validation record in the source repository covers the blocked attempts and the fixes that followed.</p><div className="landing-actions"><a className="primary" href={DEMO_RUN.pullRequest.url}>Open PR #{DEMO_RUN.pullRequest.number}</a><a className="secondary" href={`${DEMO_REPOSITORY}/commit/${DEMO_RUN.mergeCommit}`}>View merge commit</a><a className="secondary" href="https://github.com/JRH89/ForgeLoop/blob/master/docs/second-repository-validation-2026-10-09.md">Read the run record</a></div></div>
    </section>
  </>;
}
