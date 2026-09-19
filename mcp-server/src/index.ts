import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { operationFor, toolNames } from './tools.js';

const server = new Server({ name: 'forgeloop-mcp', version: '0.2.0' }, { capabilities: { tools: {} } });
server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: toolNames.map(name => ({ name, description: `ForgeLoop control-plane operation: ${name}`, inputSchema: { type: 'object', properties: { runId: { type: 'string', description: 'ForgeLoop delivery-run identifier' } }, required: ['runId'], additionalProperties: false } })) }));
server.setRequestHandler(CallToolRequestSchema, async request => { const name = request.params.name; if (!toolNames.includes(name as typeof toolNames[number])) throw new Error('Unknown ForgeLoop operation'); const runId = request.params.arguments?.runId; if (typeof runId !== 'string' || !runId.trim()) throw new Error('runId is required'); return { content: [{ type: 'text', text: JSON.stringify({ tool: name, runId, operation: operationFor(name as typeof toolNames[number]), note: 'This MCP server is a control-plane boundary. Repository checkout, test execution, browser access, and local MCP tools run only on an authorized ForgeLoop Runner.' }) }] }; });
await server.connect(new StdioServerTransport());
