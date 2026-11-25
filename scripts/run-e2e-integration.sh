#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export API_URL="${API_URL:-http://localhost:18080/api/v1}"
export WS_URL="${WS_URL:-ws://localhost:18080/ws}"
cd "$ROOT/web-app"
exec node scripts/e2e-integration.mjs
