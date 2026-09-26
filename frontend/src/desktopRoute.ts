/** Recover legacy OAuth redirects without approving anything or exposing the pairing proof. */
export function isRunnerPairingRoute(pathname: string, hash: string): boolean {
  if (pathname === '/app/runner-connect') return true;
  if (!['/app', '/app/', '/app/runner/', '/app/runner-connect/'].includes(pathname)) return false;
  const parameters = new URLSearchParams(hash.slice(1));
  const name = parameters.get('name') ?? '';
  return /^[a-f0-9]{64}$/.test(parameters.get('challenge') ?? '') && name.trim().length > 0 && name.length <= 100;
}
