import MarkdownContent from "./MarkdownContent";
import runnerSetup from "./docs/runner-setup.md?raw";

const lifecycle = [
  ["1", "Connect", "Install the GitHub App and authorize only the repositories ForgeLoop may operate on."],
  ["2", "Intake", "Add the configured intake label and, when required, assign an issue before it is eligible for a runner."],
  ["3", "Execute", "An enrolled runner plans the work, delegates bounded tasks, integrates commits, and records provider usage."],
  ["4", "Verify", "Repository-policy commands, acceptance criteria, independent review, and evidence must all pass."],
  ["5", "Deliver", "An administrator approves the verified run. ForgeLoop opens a PR and, when enabled, safely auto-merges it."],
];

const settings = [
  ["Maximum run budget", "The organization-wide USD ceiling for one run. A repository may impose a lower limit. Submissions above either limit are rejected before work starts."],
  ["Parallel task ceiling", "The largest number of independent tasks the organization may execute concurrently. Higher values can finish work faster but consume provider and runner capacity faster."],
  ["Allowed providers", "A comma-separated allowlist: anthropic, openai, gemini, or local. A harness cannot select a provider outside this list."],
  ["Require human approval", "Keeps final delivery behind an administrator decision after every ForgeLoop verification gate passes. Recommended for production repositories."],
  ["Auto-merge", "When enabled, ForgeLoop opens a ready PR and requests a squash merge only after every GitHub check and commit status succeeds and the PR head still equals the verified commit. It does not bypass ForgeLoop verification or required human approval."],
];

/** Product documentation kept inside the operator workflow and available to every role. */
export default function UserGuidePage() {
  return (
    <article className="guide">
      <section className="hero guide-hero"><p className="eyebrow">Operator handbook</p><h1>Using ForgeLoop</h1><p>From GitHub issue to verified pull request: setup, operating procedures, safety boundaries, and every configurable setting.</p></section>
      <section className="panel guide-section" id="quick-start">
        <p className="eyebrow">Start here</p><h2>How delivery works</h2>
        <div className="guide-steps">{lifecycle.map(([number,title,body])=><div key={number}><span>{number}</span><div><h3>{title}</h3><p>{body}</p></div></div>)}</div>
        <div className="guide-callout"><b>ForgeLoop works with any authorized repository.</b><span>Connect your own repositories and define the workflow and verification commands through repository policy.</span></div>
      </section>
      <section className="panel guide-section" id="runner-setup">
        <MarkdownContent content={runnerSetup} />
      </section>
      <section className="panel guide-section" id="repositories">
        <p className="eyebrow">One-time setup</p><h2>Connect a repository</h2>
        <ol><li>Open <b>Repositories</b> and select <b>Install GitHub App</b>.</li><li>In GitHub, choose the account and grant access to selected repositories. Prefer selected access over all repositories.</li><li>Return to ForgeLoop. The synchronized repository appears with its default branch, intake label, optional assignment gate, verification gates, and policy revision.</li><li>Use the safe issue draft for a title and acceptance-focused body. It does not apply labels or assignments. Add the required label and, if configured, assign the issue only when you want it to become eligible for intake.</li></ol>
        <h3>Repository run budget</h3><p>Administrators can set a maximum run budget for each connected repository. Issue-triggered work is automatically capped at the lower of the repository and organization limits, so the organization setting remains a hard ceiling even when an older repository setting is higher. Changing a limit affects future runs only; existing runs retain their recorded budget.</p>
        <h3>GitHub App requirements</h3><p>Set repository permissions to Contents: read/write, Checks: read/write, Issues: read/write, Pull requests: read/write, and Commit statuses: read-only. Issues write is used only after an administrator explicitly creates an issue from a reviewed scan proposal or AI chat draft. Those issues are not labeled or assigned automatically. GitHub grants Metadata: read-only automatically. Subscribe the App to Issues, Check run, and Check suite events; ForgeLoop also accepts GitHub's installation lifecycle deliveries. ForgeLoop validates signed webhooks and issues short-lived installation tokens, so users never paste an installation ID into the console.</p>
      </section>
      <section className="panel guide-section" id="team">
        <p className="eyebrow">Administrator setup</p><h2>Invite your team</h2>
        <p>Anyone with a GitHub account can sign in to ForgeLoop and receive a separate workspace. Signing in does not join someone else's organization or start paid work.</p>
        <p>Open <b>Team</b> in the console, enter a person's GitHub username, and choose Viewer, Operator, or Administrator. ForgeLoop resolves the name to GitHub's permanent account ID before granting access. Send the person your ForgeLoop URL; no invitation email is sent.</p>
        <p>The invitation remains pending until their first GitHub sign-in. Administrators can remove pending invitations and existing members. Removing someone revokes their next request; the last administrator cannot be removed. People in multiple organizations can switch workspaces from the console header or mobile navigation.</p>
      </section>
      <section className="panel guide-section" id="runs">
        <p className="eyebrow">Daily operation</p><h2>Monitor and control a run</h2>
        <div className="guide-grid"><div><h3>Automatic issue intake</h3><p>An issue creates one idempotent run only after its required label and assignment rule are met. Repositories can wait for any assignee or a specific GitHub login. Opened, labeled, assigned, and reopened events are evaluated; webhook retries do not duplicate the run. Keep an eligible issue away from a running worker until paid work is intended.</p></div><div><h3>Read-only intake diagnosis</h3><p>In Getting started, paste either the issue number or its GitHub issue URL. ForgeLoop confirms the link matches the selected repository, then reads eligibility rules without editing the issue, replaying webhooks, or submitting a run.</p></div><div><h3>Repository issue scan</h3><p>An administrator can manually ask an online runner to inspect a bounded, redacted snapshot using its configured provider. The scan is read-only, may incur provider charges, and saves proposed findings with evidence and estimated usage. It does not run on a schedule, edit files, or create issues automatically; an administrator reviews and explicitly publishes each proposal.</p></div><div><h3>AI issue chat</h3><p>Describe a change in a conversation and let the enrolled runner draft a structured issue specification. Each message may incur provider charges. Revise the spec, then have an administrator explicitly create the issue; it remains outside intake until the repository's label and assignee rules are satisfied. The chat follows its run, verification evidence, and pull request.</p></div><div><h3>Manual intake</h3><p>Administrators can choose <b>+ New run</b>, select an authorized repository, supply a unique source reference, title, acceptance-focused specification, and budget.</p></div><div><h3>Evidence</h3><p>Open a run to inspect its task graph, attempts, provider usage, verification output, independent review, artifacts, screenshots, escalations, and immutable audit history.</p></div><div><h3>Intervention</h3><p>Administrators may retry bounded failures, acknowledge or resolve escalations, cancel a non-terminal run, override a gate with a recorded reason, or approve a fully verified run.</p></div></div>
        <h3>Important states</h3><dl className="guide-definitions"><dt>RECEIVED / PLANNING</dt><dd>Intake was accepted and the task graph is being created.</dd><dt>QUEUED / EXECUTING</dt><dd>Work is waiting for or running on an enrolled runner.</dd><dt>BLOCKED / FAILED</dt><dd>A gate, budget, retry boundary, or execution step needs attention. Read the evidence and escalation before acting.</dd><dt>READY_FOR_REVIEW</dt><dd>Required verification and acceptance coverage passed; an administrator may approve delivery.</dd><dt>COMPLETE</dt><dd>The expected verified commit was delivered and its final outcome was recorded.</dd></dl>
      </section>
      <section className="panel guide-section" id="policy">
        <p className="eyebrow">Harness &amp; policy</p><h2>What every setting does</h2><dl className="guide-settings">{settings.map(([name,description])=><div key={name}><dt>{name}</dt><dd>{description}</dd></div>)}</dl>
        <h3>Harness definitions</h3><p>A harness is a reusable, versioned workflow profile. Its allowed roles constrain which agent roles the planner may create, and its attempt budget limits retries. Use separate harnesses for materially different workflows such as full-stack features and focused bug fixes.</p>
        <h3>Runner-local MCP routes</h3><p>An MCP route starts an allowlisted local process on the runner and exposes one context tool only to selected roles. The command and arguments identify the stdio server; tool arguments must be valid JSON. Secrets remain on the runner and must never be placed in tool arguments or the control plane.</p>
      </section>
      <section className="panel guide-section" id="delivery">
        <p className="eyebrow">Safe release</p><h2>Approval, pull requests, and auto-merge</h2><p>Approval is available only after ForgeLoop's immutable verification snapshot passes. Delivery checks that the runner-pushed branch still points to the integrated SHA, publishes the ForgeLoop check, and creates exactly one PR.</p>
        <div className="guide-grid"><div><h3>Auto-merge off</h3><p>The PR is created as a draft. Review it in GitHub, mark it ready when appropriate, and merge it manually.</p></div><div><h3>Auto-merge on</h3><p>The PR is created ready for review. ForgeLoop waits for all GitHub checks, revalidates the exact head SHA, and requests a squash merge. Failed or pending checks never merge.</p></div></div>
        <div className="guide-callout warning"><b>Enable auto-merge deliberately.</b><span>Configure branch protection, required external checks, and GitHub App permissions first. The policy applies to new deliveries; the choice is snapshotted on the publication record.</span></div>
      </section>
      <section className="panel guide-section" id="support">
        <p className="eyebrow">Get help</p><h2>Support tickets</h2><p>Use the <a href="/contact">contact form</a> with or without an account. Save the private tracking link shown after submission to read replies, track status, and respond. Anyone with the link can access the ticket; do not share it publicly. Email notifications are not enabled.</p><p>Tickets submitted while signed in also appear in <a href="/support#mine">My tickets</a>. Guest requests are not linked by email. If you lose a guest link, submit a new request with its reference. Do not include API keys, passwords, payment details, or sensitive repository contents.</p><p>Status changes and replies refresh every 15 seconds. Replying to a resolved or waiting ticket reopens it; closed tickets require a new request. Service-owner administrators can use the support inbox to reply, change status, and add internal notes that customers cannot see.</p>
      </section>
      <section className="panel guide-section" id="troubleshooting">
        <p className="eyebrow">Diagnosis</p><h2>When something does not move</h2><dl className="guide-definitions"><dt>Issue was not received</dt><dd>Confirm the App is installed on that repository, the exact configured label is present, the issue has a non-empty body, and the webhook delivery received a 202 response.</dd><dt>No task is running</dt><dd>Confirm an enrolled runner is online, has the required capability, can reach the control plane, and has its provider key in the runner process environment.</dd><dt>Run is blocked</dt><dd>Open its failed task, verification evidence, review evidence, and escalation. Repair the underlying problem, then use the bounded retry control.</dd><dt>PR was not created</dt><dd>The run must be READY_FOR_REVIEW, approved, and associated with the exact branch SHA reported by the runner.</dd><dt>PR did not auto-merge</dt><dd>Confirm auto-merge was enabled before delivery, the PR is not a draft, all GitHub checks and statuses succeeded, the head SHA did not change, and the App can merge pull requests.</dd></dl>
        <p className="guide-footer">Never solve a delivery problem by weakening verification, sharing a long-lived token, or editing ForgeLoop's managed branch outside the runner.</p>
      </section>
    </article>
  );
}
