import { readFile } from "node:fs/promises";
import { createPrivateKey, sign } from "node:crypto";
import process from "node:process";

const webhookUrl = process.argv[2];
if (!webhookUrl?.startsWith("https://")) {
  throw new Error("Usage: node scripts/Update-GithubAppWebhook.mjs <https-webhook-url>");
}

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

const response = await fetch("https://api.github.com/app/hook/config", {
  method: "PATCH",
  headers: {
    Accept: "application/vnd.github+json",
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "X-GitHub-Api-Version": "2026-03-10",
  },
  body: JSON.stringify({
    url: webhookUrl,
    content_type: "json",
    insecure_ssl: "0",
    // GitHub removes an existing secret when it is omitted from this update.
    secret: webhookSecret,
  }),
});

if (!response.ok) {
  throw new Error(`GitHub webhook update failed with HTTP ${response.status}`);
}
const configuration = await response.json();
console.log(`GitHub App webhook updated: ${configuration.url}`);
