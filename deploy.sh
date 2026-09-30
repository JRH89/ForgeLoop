#!/usr/bin/env bash
set -Eeuo pipefail

cd -- "$(dirname -- "$(realpath -- "$0")")"

# Serialize pushes so overlapping webhook deliveries cannot race each other.
exec 9>/tmp/forgeloop-deploy.lock
flock -n 9 || { echo 'A ForgeLoop deployment is already running.' >&2; exit 1; }

if [[ $(git branch --show-current) != master ]]; then
  echo 'Refusing to deploy: this checkout is not on master.' >&2
  exit 1
fi
if [[ -n $(git status --porcelain --untracked-files=normal) ]]; then
  echo 'Refusing to deploy: this checkout has uncommitted changes.' >&2
  exit 1
fi

# Gitea is the deployment source. Never serve uncommitted or stale code.
GIT_TERMINAL_PROMPT=0 git pull --ff-only origin master
export FORGELOOP_REVISION
FORGELOOP_REVISION=$(git rev-parse --short HEAD)

# The shared webhook service sets FORCE_CLEAN_BUILD=true. Keep even that
# no-cache build limited to application images. Do not stop Postgres, backups,
# or the dedicated Cloudflare connector, and never remove their volumes.
if [[ ${FORCE_CLEAN_BUILD:-false} == true ]]; then
  docker compose build --no-cache control-plane web
else
  docker compose build control-plane web
fi
docker compose up -d --no-deps --wait --wait-timeout 180 control-plane web

api_address=$(docker compose port control-plane 8090)
web_address=$(docker compose port web 80)
curl --fail --silent --show-error --max-time 10 "http://${api_address}/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error --head --max-time 10 "http://${web_address}/" >/dev/null

echo "ForgeLoop deployment healthy at revision ${FORGELOOP_REVISION}."
