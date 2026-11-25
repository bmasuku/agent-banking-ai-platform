#!/usr/bin/env bash
# Rebuild the five-agent demonstration dataset through the admin API (ledger-backed seed).
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
API="http://localhost:${BACKEND_PORT}/api/v1"
ADMIN_USER="${BOOTSTRAP_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${BOOTSTRAP_ADMIN_PASSWORD:-${DEMO_DEFAULT_PASSWORD:-Demo@2026!}}"

echo "==> Waiting for backend at ${API} ..."
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:${BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    echo "ERROR: backend is not healthy. Start the stack with ./start.sh first."
    exit 1
  fi
  sleep 2
done

echo "==> Logging in as ${ADMIN_USER}..."
TOKEN="$(curl -fsS -X POST "${API}/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')"

echo "==> Resetting demonstration data (this can take ~15–30s)..."
RESULT="$(curl -fsS -X POST "${API}/admin/demo/reset" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"confirm":"RESET"}')"

echo "$RESULT" | python3 -c "
import json,sys
d=json.load(sys.stdin)
s=d.get('seed', {})
print('Demo reset complete.')
print('  agents=%s customers=%s txs=%s advances=%s investigations=%s' % (
    s.get('agents'), s.get('customers'), s.get('transactionsCompleted'),
    s.get('advances'), s.get('investigations')))
print('  %s' % d.get('message', ''))
print('All sessions were invalidated — sign in again with demo credentials.')
"
