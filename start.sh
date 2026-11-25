#!/usr/bin/env bash
# Start the full local Docker Compose stack (Postgres, AI, Spring Boot, web).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "No .env found — copying .env.example → .env"
  cp .env.example .env
  echo "Edit .env before any non-local deployment. Continuing with example defaults for local demo."
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

COMPOSE=(docker compose -f docker-compose.yml)
if [[ "${USE_PROD_COMPOSE:-}" == "1" ]]; then
  COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
fi

echo "==> Building and starting Agent Banking stack..."
"${COMPOSE[@]}" up -d --build

echo "==> Waiting for healthy services..."
deadline=$((SECONDS + 420))
for cname in agent-banking-postgres agent-banking-ai agent-banking-backend agent-banking-web; do
  while true; do
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cname" 2>/dev/null || echo missing)"
    if [[ "$health" == "healthy" ]]; then
      echo "    $cname: healthy"
      break
    fi
    if (( SECONDS >= deadline )); then
      echo "ERROR: timed out waiting for $cname (status=$health)"
      "${COMPOSE[@]}" ps || true
      docker logs --tail=100 "$cname" 2>&1 || true
      exit 1
    fi
    sleep 4
  done
done

BACKEND_PORT="${BACKEND_PORT:-18080}"
AI_PORT="${AI_PORT:-8000}"
WEB_PORT="${WEB_PORT:-8088}"
POSTGRES_PORT="${POSTGRES_PORT:-55432}"

echo ""
echo "Stack is up."
echo "  Web portal:     http://localhost:${WEB_PORT}"
echo "  Backend API:    http://localhost:${BACKEND_PORT}/api/v1"
echo "  Swagger UI:     http://localhost:${BACKEND_PORT}/swagger-ui.html"
echo "  AI health:      http://localhost:${AI_PORT}/health"
echo "  Postgres:       localhost:${POSTGRES_PORT}"
echo ""
echo "Mobile (Expo, host machine):"
echo "  cd mobile-app && EXPO_PUBLIC_API_URL=http://localhost:${BACKEND_PORT}/api/v1 npx expo start"
echo "  Android emulator: EXPO_PUBLIC_API_URL=http://10.0.2.2:${BACKEND_PORT}/api/v1"
echo ""
echo "Demo credentials: see DEMO_GUIDE.md (password from DEMO_DEFAULT_PASSWORD in .env)."
echo "Reset demo data:  ./reset-demo.sh"
