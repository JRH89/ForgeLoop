import { describe, expect, it } from 'vitest';
import { operationFor, toolNames } from './tools.js';

describe('MCP control-plane boundary', () => {
  it('exposes generic delivery operations rather than target-repository commands', () => {
    expect(toolNames).toContain('get_repository_policy');
    expect(toolNames).not.toContain('run_backend_tests');
  });
  it('does not expose arbitrary shell execution', () => expect(operationFor('get_task_context')).not.toMatch(/cd |docker|npm|mvn/i));
});
