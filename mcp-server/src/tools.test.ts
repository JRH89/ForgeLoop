import { afterEach, describe, expect, it, vi } from 'vitest';
import { invokeControlPlane } from './gateway.js';
import { definitions, permittedTools, toolNames } from './tools.js';

afterEach(() => vi.unstubAllGlobals());

describe('permissioned MCP control-plane gateway', () => {
  it('defaults to read-only operations and exposes no shell execution', () => {
    expect(permittedTools(undefined)).toEqual(['get_run_context', 'read_verification_evidence', 'read_audit_timeline']);
    expect(toolNames).not.toContain('run_backend_tests');
    expect(Object.values(definitions).map(value => value.query).join(' ')).not.toMatch(/docker|npm|mvn|shell/i);
  });
  it('rejects unknown grants during startup', () => expect(() => permittedTools('get_run_context,shell')).toThrow('Unknown MCP tool grant'));
  it('forwards only declared arguments with bearer authentication', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ data: { featureRun: { id: 'run-1' } } }) });
    vi.stubGlobal('fetch', fetch);
    await expect(invokeControlPlane('get_run_context', { runId: 'run-1', injected: 'ignored' }, { endpoint: 'https://forgeloop.example/graphql', accessToken: 'token' })).resolves.toEqual({ featureRun: { id: 'run-1' } });
    const init = fetch.mock.calls[0][1] as RequestInit;
    expect(init.headers).toMatchObject({ Authorization: 'Bearer token' });
    expect(JSON.parse(String(init.body)).variables).toEqual({ runId: 'run-1' });
  });
  it('requires HTTPS except for local development', async () => {
    await expect(invokeControlPlane('get_run_context', { runId: 'run-1' }, { endpoint: 'http://example.com/graphql', accessToken: 'token' })).rejects.toThrow('must use HTTPS');
  });
});
