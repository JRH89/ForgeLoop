/** Permission-scoped cloud operations. Repository execution tools intentionally live on the runner. */
export const toolNames = ['get_run_context', 'read_verification_evidence', 'read_audit_timeline', 'approve_feature_run', 'cancel_feature_run', 'retry_feature_task'] as const;
export type ToolName = typeof toolNames[number];

export type ToolDefinition = { description: string; mutation: boolean; required: string[]; query: string };
export const definitions: Record<ToolName, ToolDefinition> = {
  get_run_context: { description: 'Read a tenant-authorized delivery run, task DAG, budgets, and gate state.', mutation: false, required: ['runId'], query: 'query($runId: ID!) { featureRun(id: $runId) { id repository sourceRef title state budgetUsd spentCostMicros policyRevision approved tasks { id planKey role title state attempts attemptBudget dependencyKeys } gates { name required state } criteria { statement coverageState } } }' },
  read_verification_evidence: { description: 'Read redacted, checksummed verification evidence for a delivery run.', mutation: false, required: ['runId'], query: 'query($runId: ID!) { featureRunEvidence(runId: $runId) { id taskId kind gate image command exitCode timedOut output digest recordedAt } }' },
  read_audit_timeline: { description: 'Read immutable operator and delivery audit events for a run.', mutation: false, required: ['runId'], query: 'query($runId: ID!) { featureRunAuditEvents(runId: $runId) { id actor action payloadDigest occurredAt } }' },
  approve_feature_run: { description: 'Approve a fully verified run for GitHub delivery.', mutation: true, required: ['runId', 'confirmation'], query: 'mutation($runId: ID!, $confirmation: String!) { approveFeatureRun(runId: $runId, confirmation: $confirmation) { id state approved approvedAt approvedBy } }' },
  cancel_feature_run: { description: 'Cancel a non-terminal run and hold active tasks.', mutation: true, required: ['runId', 'confirmation'], query: 'mutation($runId: ID!, $confirmation: String!) { cancelFeatureRun(runId: $runId, confirmation: $confirmation) { id state } }' },
  retry_feature_task: { description: 'Grant one audited repair attempt to a failed or held task.', mutation: true, required: ['taskId', 'reason', 'confirmation'], query: 'mutation($taskId: ID!, $reason: String!, $confirmation: String!) { retryFeatureTask(taskId: $taskId, reason: $reason, confirmation: $confirmation) { id state attempts attemptBudget } }' }
};

export function permittedTools(raw: string | undefined): ToolName[] {
  const grants = new Set((raw ?? 'get_run_context,read_verification_evidence,read_audit_timeline').split(',').map(value => value.trim()).filter(Boolean));
  const unknown = [...grants].filter(value => !toolNames.includes(value as ToolName));
  if (unknown.length) throw new Error(`Unknown MCP tool grant: ${unknown.join(', ')}`);
  return toolNames.filter(name => grants.has(name));
}
