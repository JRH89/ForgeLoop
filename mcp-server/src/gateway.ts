import { definitions, type ToolName } from './tools.js';

export type GatewayConfig = { endpoint: string; accessToken: string };

/** Calls only static GraphQL documents; tool arguments can never become source or shell text. */
export async function invokeControlPlane(tool: ToolName, args: Record<string, unknown>, config: GatewayConfig): Promise<unknown> {
  if (!config.endpoint.startsWith('https://') && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(config.endpoint)) throw new Error('FORGELOOP_GRAPHQL_URL must use HTTPS outside localhost');
  if (!config.accessToken.trim()) throw new Error('FORGELOOP_ACCESS_TOKEN is required');
  const definition = definitions[tool];
  for (const key of definition.required) if (typeof args[key] !== 'string' || !(args[key] as string).trim()) throw new Error(`${key} is required`);
  const variables = Object.fromEntries(definition.required.map(key => [key, args[key]]));
  const response = await fetch(config.endpoint, { method: 'POST', headers: { Authorization: `Bearer ${config.accessToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ query: definition.query, variables }) });
  const body = await response.json() as { data?: unknown; errors?: Array<{ message?: string }> };
  if (!response.ok || body.errors?.length || !body.data) throw new Error(body.errors?.[0]?.message ?? `ForgeLoop returned HTTP ${response.status}`);
  return body.data;
}
