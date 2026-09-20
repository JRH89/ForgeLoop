const endpoint = import.meta.env.VITE_GRAPHQL_URL ?? '/graphql';

export type Task = { id: string; role: string; title: string; state: string; attemptBudget: number; attempts: number };
export type Gate = { id: string; name: string; required: boolean; state: string };
export type Criterion = { id: string; statement: string; coverageState: string };
export type FeatureRun = { id: string; repository: string; sourceRef: string; title: string; specification: string; budgetUsd: number; state: string; createdAt: string; tasks: Task[]; gates: Gate[]; criteria: Criterion[] };
export type SubmitFeature = { repository: string; sourceRef: string; title: string; specification: string; budgetUsd: number };
export type RepositoryConnection = { id: string; repository: string; installationId: number; enabled: boolean; defaultBranch: string; issueLabel: string; harnessProfile: string; requiredGates: string[]; maxBudgetUsd: number; policyRevision: number };
export type ConnectRepository = Omit<RepositoryConnection, 'id' | 'enabled' | 'policyRevision'>;

async function request<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ query, variables }) });
  const body = await response.json() as { data?: T; errors?: Array<{ message?: string }> };
  if (!response.ok || body.errors?.length || !body.data) throw new Error(body.errors?.[0]?.message ?? 'ForgeLoop control plane request failed');
  return body.data;
}

export function loadRuns(): Promise<FeatureRun[]> {
  return request<{ featureRuns?: FeatureRun[] }>('query { featureRuns { id repository sourceRef title specification budgetUsd state createdAt tasks { id role title state attemptBudget attempts } gates { id name required state } criteria { id statement coverageState } } }').then(data => data.featureRuns ?? []);
}

export function submitFeature(input: SubmitFeature): Promise<FeatureRun> {
  return request<{ submitFeature: FeatureRun }>('mutation($input: SubmitFeatureInput!) { submitFeature(input: $input) { id repository sourceRef title specification budgetUsd state createdAt tasks { id role title state attemptBudget attempts } gates { id name required state } criteria { id statement coverageState } } }', { input }).then(data => data.submitFeature);
}

/** Cancels an in-flight run through the audited operator mutation. */
export function cancelFeatureRun(runId: string): Promise<FeatureRun> {
  return request<{ cancelFeatureRun: FeatureRun }>('mutation($runId: ID!) { cancelFeatureRun(runId: $runId) { id repository sourceRef title specification budgetUsd state createdAt tasks { id role title state attemptBudget attempts } gates { id name required state } criteria { id statement coverageState } } }', { runId }).then(data => data.cancelFeatureRun);
}

export function loadRepositoryConnections(): Promise<RepositoryConnection[]> { return request<{ repositoryConnections?: RepositoryConnection[] }>('query { repositoryConnections { id repository installationId enabled defaultBranch issueLabel harnessProfile requiredGates maxBudgetUsd policyRevision } }').then(data => data.repositoryConnections ?? []); }
export function connectRepository(input: ConnectRepository): Promise<RepositoryConnection> { return request<{ connectRepository: RepositoryConnection }>('mutation($input: ConnectRepositoryInput!) { connectRepository(input: $input) { id repository installationId enabled defaultBranch issueLabel harnessProfile requiredGates maxBudgetUsd policyRevision } }', { input }).then(data => data.connectRepository); }
