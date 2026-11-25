# Full-stack integration

## Running stack

| Component | Default |
| --- | --- |
| PostgreSQL | `localhost:55432` (`agent_banking` / `agent_banking`) |
| Spring Boot | `http://localhost:18080` |
| FastAPI AI | `http://localhost:8000` (internal key `local-dev-internal-key-change-me`) |
| Web portal | `http://localhost:5173` (proxies `/api` → backend) |
| Mobile Expo | Metro on `8082`; API `http://localhost:18080/api/v1` |

Start AI (Docker):

```bash
docker run -d --name agent-banking-ai --rm -p 8000:8000 \
  -e AI_INTERNAL_API_KEY=local-dev-internal-key-change-me \
  -e BACKEND_BASE_URL=http://host.docker.internal:18080 \
  agent-banking-ai:local
```

Start backend with AI enabled:

```bash
DB_URL=jdbc:postgresql://localhost:55432/agent_banking \
SERVER_PORT=18080 \
AI_SERVICE_ENABLED=true \
AI_SERVICE_URL=http://localhost:8000 \
AI_SERVICE_API_KEY=local-dev-internal-key-change-me \
java -jar backend/target/agent-banking-backend-1.0.0.jar
```

## Realtime (STOMP)

- Endpoint: `/ws` (Bearer JWT on CONNECT)
- Topics: `/topic/agents/{id}`, `/topic/areas/{id}`, `/topic/risk`, `/topic/finance`, `/user/queue/notifications`
- Web: `web-app/src/hooks/useRealtime.ts`
- Mobile: `mobile-app/src/hooks/useRealtime.ts`

Events include transactions, exchanges, replenishments, commission disbursements, fraud alerts, low-float, and advance decisions.

## E2E demo workflow

Persona map (story → seed account):

| Story | Account |
| --- | --- |
| John | `agent1` |
| Peter | `agent2` |
| Mary | `agent3` |
| David | `agent5` |
| Sarah | `agent4` (falls back to `agent1` if CRITICAL auto-reject) |
| Risk / Finance / AM | `risk` / `finance` / `manager` |

Password for all: `Demo@2026!`

```bash
./scripts/run-e2e-integration.sh
```

The script authenticates against Spring Boot, exercises money movement through the ledger, AI fraud/credit, WebSocket fraud alerts, area replenishment, and commission recovery — asserting consistency across agent and staff APIs on the same database.
