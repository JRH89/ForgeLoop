import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const projectDirectory = fileURLToPath(new URL('..', import.meta.url));
const configuration = JSON.parse(execFileSync(
  'docker',
  ['compose', '--profile', 'tunnel', 'config', '--format', 'json'],
  {
    cwd: projectDirectory,
    encoding: 'utf8',
    env: {
      ...process.env,
      CLOUDFLARED_CREDENTIALS_FILE: process.env.CLOUDFLARED_CREDENTIALS_FILE || '/tmp/forgeloop-tunnel.json',
    },
    stdio: ['ignore', 'pipe', 'inherit'],
  },
));

// Every host-published service is for local diagnostics; Cloudflare reaches the app over Compose DNS.
for (const serviceName of ['postgres', 'control-plane', 'web', 'webhook-edge']) {
  const ports = configuration.services[serviceName]?.ports ?? [];
  if (ports.length === 0 || ports.some(({ host_ip }) => host_ip !== '127.0.0.1')) {
    throw new Error(`Compose service ${serviceName} must publish diagnostic ports on 127.0.0.1 only`);
  }
}

if ((configuration.services.cloudflared?.ports ?? []).length !== 0) {
  throw new Error('The Cloudflare connector must not publish host ports');
}

// The database, application, and backup worker must authenticate with one configured secret.
const databasePassword = configuration.services.postgres.environment.POSTGRES_PASSWORD;
if (
  databasePassword !== configuration.services['control-plane'].environment.SPRING_DATASOURCE_PASSWORD
  || databasePassword !== configuration.services['database-backup'].environment.PGPASSWORD
) {
  throw new Error('PostgreSQL, control-plane, and backup database credentials must match');
}

console.log('Compose network and database-secret boundary checks passed.');
