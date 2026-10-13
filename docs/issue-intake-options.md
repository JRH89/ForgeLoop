# Issue intake options

Settings → Repository settings has independent label and assignment gates.
Require intake label is on by default for new and existing connections. An
administrator can turn it off after acknowledging that new eligible issues may
start paid work without labels. Disabled repositories, closed issues, pull
requests and blank issue bodies remain ineligible. Any configured assignee rule
still applies, as do budgets, verification, human approval and merge protection.

Issue Chat has an Add intake label automatically checkbox at issue publication.
It defaults off, uses the saved repository label (not a hardcoded label), and
adds it in the same GitHub issue-creation request. No assignee is added and no
draft is published without administrator review. Confirmation warns about paid
intake. Scan proposals remain unlabeled, but label bypass can make them eligible.

The setting applies on future opened/labeled/assigned/reopened webhook events;
it does not rescan or launch existing issues merely because the setting changed.
Previously captured run policies are unchanged. Changes and publications are
audited. Older clients that omit the new optional argument preserve label policy.

The public /changelog page is prerendered, linked in the footer, indexed through
the sitemap and includes selected shipped milestones. Add factual dated entries
with release/PR references; do not describe future roadmap work as shipped.
