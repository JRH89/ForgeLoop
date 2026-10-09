# Delivery approval and automatic merge

Delivery honors the organization policy captured in each run's checksummed policy
snapshot. Later organization edits do not retroactively change that run's approval
or merge requirements.

| Require human approval | Auto-merge | After verification passes |
| --- | --- | --- |
| On | On | Wait for approval, then publish a ready PR and merge after GitHub checks. |
| On | Off | Wait for approval, then publish a draft PR for manual delivery. |
| Off | On | Publish automatically, then merge after GitHub checks. |
| Off | Off | Publish a draft PR automatically; merging remains manual. |

The verified-delivery reconciler checks pending pushed branches every 30 seconds.
It requires READY_FOR_REVIEW, delivery authorization, an enabled repository
connection in the same organization, and the exact verified remote head. Existing
verification, criterion coverage, GitHub check, and merge-head checks are unchanged.
Failures remain pending for reconciliation; no human approval event is fabricated.
Legacy runs without a valid checksummed snapshot require human approval and do not
automatically request merge.

Regression tests cover snapshot approval requirements, auto-merge opt-in, legacy
fail-closed behavior, nonverified states, cross-organization publication rejection,
and scheduled publication without a human click.

This is a control-plane change. Updating only a desktop runner does not deploy it.
