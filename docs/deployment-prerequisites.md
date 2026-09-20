# Deployment prerequisites

## GitHub App production activation

The GitHub App install entry point is implemented. Before enabling live repository synchronization, webhook intake, and pull-request delivery in a deployed environment, configure these externally managed values:

- `FORGELOOP_GITHUB_APP_SLUG`
- GitHub App ID and PEM private key for installation-token exchange
- `FORGELOOP_GITHUB_WEBHOOK_SECRET`
- A public HTTPS callback and webhook URL

These values are deployment secrets and must be supplied through the deployment secret manager, never committed to this repository.
