# Contributing

Use Java 21, Node 22+, and Docker Compose v2. Run the checks listed in the README before opening a pull request. Keep domain policy in services, add tests for new authorization rules, and do not commit evidence, credentials, or provider responses containing secrets.

For harness changes, define the state transition and failure mode first. A new verification gate must have an evidence artifact, an owner, a timeout, and a bounded repair policy.

