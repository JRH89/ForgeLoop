const endpoint = import.meta.env.VITE_GRAPHQL_URL ?? '/graphql';

export type Task = { id: string; role: string; title: string; state: string; attemptBudget: number; attempts: number };
export type Gate = { id: string; name: string; required: boolean; state: string };
export type Criterion = { id: string; statement: string; state: string };
export type FeatureRun = { id: string; repository: string; sourceRef: string; title: string; specification: string; budgetUsd: number; state: string; createdAt: string; tasks: Task[]; gates: Gate[]; criteria: Criterion[] };
export type SubmitFeature = { repository: string; sourceRef: string; title: string; specification: string; budgetUsd: number };

async function request<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ query, variables }) });
  const body = await response.json() as { data?: T; errors?: Array<{ message?: string }> };
  if (!response.ok || body.errors?.length || !body.data) throw new Error(body.errors?.[0]?.message ?? 'ForgeLoop control plane request failed');
  return body.data;
}

export function loadRuns(): Promise<FeatureRun[]> {
  return request<{ featureRuns: FeatureRun[] }>('query { featureRuns { id repository sourceRef title specification budgetUsd state createdAt tasks { id role title state attemptBudget attempts } gates { id name required state } criteria { id statement state } } }').then(data => data.featureRuns);
}

export function submitFeature(input: SubmitFeature): Promise<FeatureRun> {
  return request<{ submitFeature: FeatureRun }>('mutation($input: SubmitFeatureInput!) { submitFeature(input: $input) { id repository sourceRef title specification budgetUsd state createdAt tasks { id role title state attemptBudget attempts } gates { id name required state } criteria { id statement state } } }', { input }).then(data => data.submitFeature);
}
