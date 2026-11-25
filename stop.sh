#!/usr/bin/env bash
# Stop the Docker Compose stack. Use STOP_VOLUMES=1 to also delete database data.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml)
if [[ -f docker-compose.prod.yml && "${USE_PROD_COMPOSE:-}" == "1" ]]; then
  COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
fi

if [[ "${STOP_VOLUMES:-}" == "1" ]]; then
  echo "==> Stopping stack and removing volumes (database will be wiped)..."
  "${COMPOSE[@]}" down -v --remove-orphans
else
  echo "==> Stopping stack (volumes preserved)..."
  "${COMPOSE[@]}" down --remove-orphans
fi

echo "Stopped."
