# Consolidated console settings

Settings is the single editing location for organization spending/delivery policy,
repository budgets, assignment intake and retained run records. Repository pages
show summaries and link to Settings; they retain repository scans. Runner setup,
harnesses and local MCP configuration are in a collapsed advanced section.

The organization ceiling remains a safety boundary, not a second per-run budget.
New manual runs inherit the selected repository limit capped by the current
organization ceiling. Issue-triggered behavior is unchanged. Existing runs keep
their captured limits; no stored settings or enforcement were removed.

The getting-started banner's dismissal is persisted in localStorage per GitHub
identity and workspace. It survives reloads in that browser; clearing storage or
using another browser resets it. Blocked storage still permits session dismissal.
Getting started remains accessible in the sidebar. No secrets are stored.
