/** Control-plane MCP operations. Repository execution remains runner-local. */
export const toolNames = ['get_run_context', 'get_repository_policy', 'get_task_context', 'read_verification_evidence', 'submit_verification_evidence', 'request_task_cancellation'] as const;
export type ToolName = typeof toolNames[number];

export function operationFor(tool: ToolName): string {
  const operations: Record<ToolName, string> = {
    get_run_context: 'Read the authorized delivery-run context from the ForgeLoop control plane.',
    get_repository_policy: 'Read the policy revision bound to the selected repository and run.',
    get_task_context: 'Read the authorized scoped task context and capability grant.',
    read_verification_evidence: 'Read evidence metadata and approved artifacts for a delivery run.',
    submit_verification_evidence: 'Submit checksummed, redacted verification evidence for the leased task.',
    request_task_cancellation: 'Request a policy-authorized cancellation; the control plane records the decision.'
  };
  return operations[tool];
}
