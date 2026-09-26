import { describe, expect, it } from 'vitest';
import { isRunnerPairingRoute } from './desktopRoute';
describe('pairing redirect recovery', () => {
  const hash = `#challenge=${'a'.repeat(64)}&name=My+runner`;
  it('recognizes the current page even with an invalid request so it can explain the error', () => {
    expect(isRunnerPairingRoute('/app/runner-connect', '')).toBe(true);
  });
  it('recovers valid fragments left on the dashboard by older login redirects', () => {
    for (const path of ['/app', '/app/', '/app/runner/', '/app/runner-connect/']) expect(isRunnerPairingRoute(path, hash)).toBe(true);
  });
  it('does not hijack ordinary navigation or malformed fragments', () => {
    expect(isRunnerPairingRoute('/app', '#challenge=bad&name=test')).toBe(false);
    expect(isRunnerPairingRoute('/app', `#challenge=${'a'.repeat(64)}`)).toBe(false);
    expect(isRunnerPairingRoute('/app/runner-downloads', hash)).toBe(false);
  });
});
