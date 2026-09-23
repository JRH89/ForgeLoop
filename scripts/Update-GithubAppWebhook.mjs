import { readFile } from "node:fs/promises";
import { createPrivateKey, sign } from "node:crypto";
import process from "node:process";

const command = process.argv[2];
const inspectDeliveries = command === "--deliveries";
const redeliveryId = command === "--redeliver" ? process.argv[3] : null;
if (!inspectDeliveries && !redeliveryId && !command?.startsWith("https://")) {
  throw new Error("Usage: node scripts/Update-GithubAppWebhook.mjs <https-webhook-url>|--deliveries|--redeliver <delivery-id>");
}
if (redeliveryId && !/^\d+$/.test(redeliveryId)) throw new Error("Delivery ID must contain only digits");

const values = Object.fromEntries(
  (await readFile(".env", "utf8"))
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => {
      const separator = line.indexOf("=");
      return [line.slice(0, separator), line.slice(separator + 1)];
    }),
);

const appId = values.FORGELOOP_GITHUB_APP_ID;
const webhookSecret = values.FORGELOOP_GITHUB_WEBHOOK_SECRET;
const privateKeyPem = values.FORGELOOP_GITHUB_PRIVATE_KEY?.replaceAll("\\n", "\n");
if (!appId || !webhookSecret || !privateKeyPem) {
  throw new Error(".env must contain the GitHub App ID, private key, and webhook secret");
}

const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
const now = Math.floor(Date.now() / 1000);
const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({ iat: now - 30, exp: now + 540, iss: appId })}`;
const signature = sign("RSA-SHA256", Buffer.from(unsigned), createPrivateKey(privateKeyPem)).toString("base64url");
const token = `${unsigned}.${signature}`;

const endpoint = inspectDeliveries
  ? "https://api.github.com/app/hook/deliveries?per_page=20"
  : redeliveryId
    ? `https://api.github.com/app/hook/deliveries/${redeliveryId}/attempts`
    : "https://api.github.com/app/hook/config";
const response = await fetch(endpoint, {
  method: inspectDeliveries ? "GET" : redeliveryId ? "POST" : "PATCH",
  headers: {
    Accept: "application/vnd.github+json",
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "X-GitHub-Api-Version": "2026-03-10",
  },
  body: inspectDeliveries || redeliveryId ? undefined : JSON.stringify({
    url: command,
    content_type: "json",
    insecure_ssl: "0",
    // GitHub removes an existing secret when it is omitted from this update.
    secret: webhookSecret,
  }),
});

if (!response.ok) throw new Error(`GitHub App webhook request failed with HTTP ${response.status}`);
if (redeliveryId) {
  console.log(`GitHub App webhook redelivery accepted: ${redeliveryId}`);
  process.exit(0);
}
// Delivery IDs exceed JavaScript's safe integer range. Quote them before parsing so diagnostics remain exact.
const result = JSON.parse((await response.text()).replace(/("id"\s*:\s*)(\d{16,})/g, '$1"$2"'));
if (inspectDeliveries) {
  console.log(JSON.stringify(result.map(({ id, event, action, status, delivered_at: deliveredAt, status_code: statusCode }) =>
    ({ id, event, action, status, deliveredAt, statusCode })), null, 2));
} else {
  console.log(`GitHub App webhook updated: ${result.url}`);
}
