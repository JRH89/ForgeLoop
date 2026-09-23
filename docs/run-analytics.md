# Run analytics

The Analytics workspace derives organization-scoped metrics from persisted feature runs and provider-attempt receipts. It reports run counts, delivery counts, request outcomes, token totals, known provider cost, and cost-data coverage. Model comparisons group by provider and model; harness comparisons group by the immutable harness profile recorded on each run.

These values are operational facts, not projections. Unknown provider pricing is excluded from cost totals and lowers the displayed coverage percentage. Comparisons with no recorded attempts remain empty rather than inventing benchmark data. Because analytics uses the same tenant-filtered run service as the operator console, a user cannot aggregate another organization's runs.

Model success counts describe successful provider requests, not software correctness. Verification gates, independent review, merge status, and end-to-end evidence remain the authoritative delivery measures.
