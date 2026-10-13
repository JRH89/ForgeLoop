# Consolidated console settings

The Repositories tab omits the introductory hero banner. Repository cards are
native keyboard-accessible disclosures, initially open. Their last open/closed
choice is saved per account, workspace and repository in localStorage; blocked
storage does not prevent toggling. This changes visibility only, not execution.

Settings is the single editing location for organization spending/delivery policy,
repository budgets, assignment intake and retained run records. Repository pages
show summaries and link to Settings; they retain repository scans. Runner setup
and execution policy appear first. Harnesses and local MCP configuration remain
in a collapsed advanced section below repository settings.

The organization ceiling remains a safety boundary, not a second per-run budget.
New manual runs inherit the selected repository limit capped by the current
organization ceiling. Issue-triggered behavior is unchanged. Existing runs keep
their captured limits; no stored settings or enforcement were removed.

The getting-started banner's dismissal is persisted in localStorage per GitHub
identity and workspace. It survives reloads in that browser; clearing storage or
using another browser resets it. Blocked storage still permits session dismissal.
Getting started remains accessible in the sidebar. No secrets are stored.
Label and assignment gates are independent. Label gating defaults on; administrators
can bypass it. Issue Chat can opt into adding the configured label on publication.
Both paths warn about potential immediate provider spending.
