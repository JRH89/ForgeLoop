# Deployment prerequisites

## GitHub App production activation

The GitHub App install entry point is implemented. Before enabling live repository synchronization, webhook intake, and pull-request delivery in a deployed environment, configure these externally managed values:

- `FORGELOOP_GITHUB_APP_SLUG`
- `FORGELOOP_GITHUB_APP_ID` and a secret-manager-injected PKCS#8 PEM private key in `FORGELOOP_GITHUB_PRIVATE_KEY` for short-lived installation-token exchange
- `FORGELOOP_GITHUB_WEBHOOK_SECRET`
- A public HTTPS callback and webhook URL

These values are deployment secrets and must be supplied through the deployment secret manager, never committed to this repository.

Configure the GitHub App's setup URL as `https://<control-plane>/api/github/app/callback`. ForgeLoop signs the installation state for ten minutes and records the resulting installation only after the callback validates that state. Configure the webhook URL as `https://<control-plane>/api/github/webhooks` and subscribe to `issues` and `installation_repositories`.

The delivery adapter creates a branch, writes runner-produced verified changes, creates a successful ForgeLoop check run, and opens one draft pull request per run. A live delivery remains a staging validation gate: use a disposable installed repository and confirm GitHub's actual API responses before enabling a production policy.
