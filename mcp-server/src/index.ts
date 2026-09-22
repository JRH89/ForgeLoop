import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { invokeControlPlane } from './gateway.js';
import { definitions, permittedTools, type ToolName } from './tools.js';

const grants = permittedTools(process.env.FORGELOOP_MCP_TOOL_GRANTS);
const config = { endpoint: process.env.FORGELOOP_GRAPHQL_URL ?? 'http://localhost:8090/graphql', accessToken: process.env.FORGELOOP_ACCESS_TOKEN ?? '' };
const server = new Server({ name: 'forgeloop-mcp', version: '0.3.0' }, { capabilities: { tools: {} } });

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: grants.map(name => ({ name, description: definitions[name].description, inputSchema: { type: 'object', properties: { runId: { type: 'string' }, taskId: { type: 'string' }, reason: { type: 'string', maxLength: 1000 }, confirmation: { type: 'string', description: 'Explicit APPROVE, CANCEL, or RETRY confirmation for mutating tools.' } }, required: definitions[name].required, additionalProperties: false } })) }));
server.setRequestHandler(CallToolRequestSchema, async request => {
  const name = request.params.name as ToolName;
  if (!grants.includes(name)) throw new Error('Tool is not granted to this MCP client');
  const data = await invokeControlPlane(name, request.params.arguments ?? {}, config);
  return { content: [{ type: 'text', text: JSON.stringify(data) }] };
});

await server.connect(new StdioServerTransport());
