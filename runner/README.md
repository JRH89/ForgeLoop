# ForgeLoop Runner

The runner executes inside customer-controlled infrastructure. It registers with the ForgeLoop control plane using a one-time token, then will claim scoped work only after lease and capability checks are implemented.

## Current capabilities

* Validates local runner configuration.
* Registers through the control-plane GraphQL API.
* Sends a runner heartbeat through the control-plane GraphQL API.
* Runs as a non-root container image.

## Container build

```sh
docker build -t forgeloop-runner:local runner
```

Registration tokens are secrets. Provide them through a secure local secret mechanism; do not put them in source control, logs, or command history.

The runner does not yet clone repositories, execute tools, or process task leases. Those capabilities are deliberately added only after their isolation and acknowledgement protocols are verified.
