#!/usr/bin/env bash
# Smoke-test the running Docker Compose environment (and optional unit suites).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

BACKEND_PORT="${BACKEND_PORT:-18080}"
AI_PORT="${AI_PORT:-8000}"
WEB_PORT="${WEB_PORT:-8088}"
API="http://localhost:${BACKEND_PORT}/api/v1"
PASS="${DEMO_DEFAULT_PASSWORD:-Demo@2026!}"
failed=0

pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; failed=$((failed + 1)); }

check_http() {
  local name="$1" url="$2" expect="${3:-200}"
  local code
  code="$(curl -s -o /tmp/ab-test-body.txt -w '%{http_code}' "$url" || echo 000)"
  if [[ "$code" == "$expect" ]]; then
    pass "$name ($code)"
  else
    fail "$name (got $code, expected $expect) — $url"
  fi
}

echo "=== Compose service health ==="
for c in agent-banking-postgres agent-banking-ai agent-banking-backend agent-banking-web; do
  h="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c" 2>/dev/null || echo missing)"
  if [[ "$h" == "healthy" ]]; then pass "$c healthy"; else fail "$c status=$h"; fi
done

echo ""
echo "=== HTTP endpoints ==="
check_http "Backend actuator" "http://localhost:${BACKEND_PORT}/actuator/health"
check_http "AI health" "http://localhost:${AI_PORT}/health"
check_http "Web healthz" "http://localhost:${WEB_PORT}/healthz"
check_http "Web UI" "http://localhost:${WEB_PORT}/"
check_http "Web → API proxy (login page path via spa)" "http://localhost:${WEB_PORT}/api/v1/auth/me" "401"

echo ""
echo "=== Auth + mobile/web API reachability ==="
LOGIN="$(curl -s -X POST "${API}/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"agent1\",\"password\":\"${PASS}\"}" || true)"
TOKEN="$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("accessToken",""))' "$LOGIN" 2>/dev/null || true)"
if [[ -n "$TOKEN" ]]; then
  pass "Agent login (mobile/web clients use this API)"
  BAL="$(curl -s -o /tmp/ab-bal.json -w '%{http_code}' "${API}/agent/balances" -H "Authorization: Bearer ${TOKEN}")"
  if [[ "$BAL" == "200" ]]; then pass "Agent balances via host API (Expo target)"; else fail "Agent balances HTTP $BAL"; fi
  NAME="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("user",{}).get("fullName",""))' <<<"$LOGIN")"
  echo "      logged in as: ${NAME:-unknown}"
else
  fail "Agent login failed — ${LOGIN:0:200}"
fi

ADMIN_LOGIN="$(curl -s -X POST "${API}/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"${BOOTSTRAP_ADMIN_PASSWORD:-$PASS}\"}" || true)"
ADMIN_TOKEN="$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("accessToken",""))' "$ADMIN_LOGIN" 2>/dev/null || true)"
if [[ -n "$ADMIN_TOKEN" ]]; then
  pass "Admin login (web portal)"
  WPROXY="$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:${WEB_PORT}/api/v1/auth/login" \
    -H 'Content-Type: application/json' -d "{\"username\":\"finance\",\"password\":\"${PASS}\"}")"
  if [[ "$WPROXY" == "200" ]]; then pass "Web nginx proxies /api to backend"; else fail "Web API proxy login HTTP $WPROXY"; fi
else
  fail "Admin login failed"
fi

if [[ "${RUN_UNIT_TESTS:-}" == "1" ]]; then
  echo ""
  echo "=== Optional unit suites (RUN_UNIT_TESTS=1) ==="
  if command -v mvn >/dev/null 2>&1; then
    (cd backend && mvn -q test) && pass "Backend mvn test" || fail "Backend mvn test"
  fi
  if [[ -x ai-service/.venv/bin/pytest ]]; then
    (cd ai-service && .venv/bin/pytest -q) && pass "AI pytest" || fail "AI pytest"
  fi
  if [[ -d web-app/node_modules ]]; then
    (cd web-app && npm test -- --run) && pass "Web vitest" || fail "Web vitest"
  fi
fi

echo ""
if [[ "$failed" -eq 0 ]]; then
  echo "All smoke checks passed."
  exit 0
fi
echo "$failed check(s) failed."
exit 1
