# AI FloatShield

Keep agents liquid. Keep risk explainable.

Local development and demonstration environment for AI float rebalancing and fraud detection: Spring Boot ledger APIs, FastAPI AI services, React staff portal, and Expo mobile apps for agents and area managers.

## Architecture

| Service | Technology | Host port (default) |
|---------|------------|---------------------|
| PostgreSQL | Postgres 16 | `55432` |
| Backend | Spring Boot 3 / Java 21 | `18080` |
| AI | FastAPI / Python 3.12 | `8000` |
| Web | React (Vite) via nginx | `8088` |
| Mobile | Expo / React Native | runs on host (not in Compose) |

Schema is applied by **Flyway** migrations in `backend/src/main/resources/db/migration` (`V1`–`V5`) when the backend starts. Postgres init scripts under `docker/postgres/init/` only enable extensions.

## Prerequisites

- Docker Desktop / Docker Engine **24+** with Compose v2
- (Mobile) Node.js **22+**, Expo CLI / `npx expo`
- (Optional host builds) JDK 21, Maven 3.9+, Python 3.12

## Quick start (Docker)

```bash
cp .env.example .env          # secrets via env — do not commit .env
./start.sh                    # build + up + wait until healthy
./test.sh                     # smoke-test health, auth, web→API proxy
./reset-demo.sh               # rebuild five-agent demo data
./stop.sh                     # stop containers (keep DB volume)
STOP_VOLUMES=1 ./stop.sh      # stop and wipe Postgres + AI volumes
```

Open:

- Web portal: http://localhost:8088  
- API / Swagger: http://localhost:18080/swagger-ui.html  
- AI health: http://localhost:8000/health  

## Environment configuration

All secrets are injected from environment variables (see `.env.example`):

| Variable | Purpose |
|----------|---------|
| `POSTGRES_PASSWORD` | Database password |
| `JWT_SECRET` | Base64 HMAC key for JWTs |
| `AI_SERVICE_API_KEY` | Shared Spring ↔ AI internal key |
| `DEMO_DEFAULT_PASSWORD` / `BOOTSTRAP_ADMIN_PASSWORD` | Demo / admin passwords |
| `AI_LLM_API_KEY` | Optional OpenAI-compatible key for the assistant |

**Never commit `.env`, API keys, or real passwords.** `.gitignore` excludes `.env`.

### Development vs production Compose

```bash
# Development / demo (default) — seeds demo data on empty DB
./start.sh

# Production-oriented overrides (no auto-seed; secrets required)
USE_PROD_COMPOSE=1 ./start.sh
# equivalent:
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## Installation (detail)

### 1. Clone and configure

```bash
cp .env.example .env
# Edit POSTGRES_PASSWORD, JWT_SECRET, AI_SERVICE_API_KEY for anything beyond a private laptop demo
```

### 2. Database startup

Postgres starts as part of `./start.sh`. Data persists in the Docker volume `agent_banking_pgdata`.

Standalone (Compose still preferred):

```bash
docker compose up -d postgres
```

### 3. Backend startup

Started by Compose (`backend` service). Flyway runs automatically.

Host-native alternative (DB must already be up):

```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:55432/agent_banking
export DB_USERNAME=agent_banking DB_PASSWORD=...
export SERVER_PORT=18080
export AI_SERVICE_URL=http://localhost:8000
export AI_SERVICE_API_KEY=...
mvn spring-boot:run
```

### 4. AI service startup

Started by Compose (`ai` service).

Host-native:

```bash
cd ai-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
export AI_INTERNAL_API_KEY=...
uvicorn main:app --reload --port 8000
```

### 5. Web application startup

**Docker:** nginx serves the production build and proxies `/api` and `/ws` to the backend (http://localhost:8088).

**Vite dev server** (hot reload against Compose backend):

```bash
cd web-app
npm install
VITE_API_URL=http://localhost:18080/api/v1 npm run dev -- --port 5173
```

### 6. Mobile application startup (Expo)

Mobile is **not** containerised. Point it at the Compose backend:

```bash
cd mobile-app
npm install

# iOS simulator / Expo web
EXPO_PUBLIC_API_URL=http://localhost:18080/api/v1 npx expo start

# Android emulator (host loopback is 10.0.2.2)
EXPO_PUBLIC_API_URL=http://10.0.2.2:18080/api/v1 npx expo start --android
```

`EXPO_PUBLIC_API_URL` is read in `mobile-app/src/config.ts`. WebSocket URL is derived from the same base (`…/ws`).

#### Android emulator configuration

1. Start an AVD from Android Studio.  
2. Ensure Docker backend publishes `BACKEND_PORT` (default **18080**).  
3. Use `http://10.0.2.2:18080/api/v1` — not `localhost`.  
4. Physical device: use your machine’s LAN IP and allow the firewall to accept TCP 18080.  
5. Clear Expo cache if the URL was changed: `npx expo start -c`.

## Demo credentials

Default password (override with `DEMO_DEFAULT_PASSWORD` in `.env`): **`Demo@2026!`**

| Role | Username |
|------|----------|
| Administrator | `admin` |
| Area manager | `manager` |
| Risk officer | `risk` |
| Finance officer | `finance` |
| Agent John | `agent1` |
| Agent Mary | `agent2` |
| Agent Peter | `agent3` |
| Agent Sarah | `agent4` |
| Agent David | `agent5` |

Persona balances, scores, and walkthrough: **[DEMO_GUIDE.md](DEMO_GUIDE.md)**.

## Demo reset instructions

```bash
./reset-demo.sh
# or Admin → Simulation Control Centre → Reset entire demonstration (confirm RESET)
```

Reset truncates operational tables and re-seeds through real services (ledger-backed). **All sessions are invalidated.**

## Scripts

| Script | Purpose |
|--------|---------|
| `./start.sh` | `compose up --build`, wait for healthy |
| `./stop.sh` | `compose down` (`STOP_VOLUMES=1` wipes data) |
| `./reset-demo.sh` | Admin API demo reset |
| `./test.sh` | Health + login + web proxy smoke tests (`RUN_UNIT_TESTS=1` for Maven/pytest/vitest) |

## Project layout

```
├── docker-compose.yml          # Dev / demo stack
├── docker-compose.prod.yml     # Production overrides
├── .env.example
├── docker/postgres/init/       # DB extensions on first volume init
├── backend/                    # Spring Boot + Flyway
├── ai-service/                 # FastAPI AI
├── web-app/                    # React portal
├── mobile-app/                 # Expo agent + area manager
├── scripts/                    # E2E / QA helpers
├── DEMO_GUIDE.md
└── docs/QA_TEST_REPORT.md
```

## Troubleshooting

| Symptom | What to try |
|---------|-------------|
| Port already allocated | Stop host processes on 18080/8000/55432/8088, or change ports in `.env`. `./stop.sh` then `docker rm -f agent-banking-backend agent-banking-ai agent-banking-postgres agent-banking-web` |
| Backend unhealthy / Flyway errors | `docker logs agent-banking-backend`. Wipe DB: `STOP_VOLUMES=1 ./stop.sh && ./start.sh` |
| Web loads but API 502 | Wait until backend is healthy; `docker logs agent-banking-web` / `agent-banking-backend` |
| Mobile cannot reach API | Confirm `EXPO_PUBLIC_API_URL`; Android emulator needs `10.0.2.2`; device needs LAN IP |
| Login fails after reset | Expected — sessions revoked; sign in again |
| AI 401 from backend | `AI_SERVICE_API_KEY` must match on `backend` and `ai` services |
| Compose missing `.env` vars | `cp .env.example .env` — required vars use `${VAR:?…}` and fail fast if unset |
| Old named containers conflict | `docker rm -f agent-banking-postgres agent-banking-ai` then `./start.sh` |

## Testing

```bash
./test.sh
RUN_UNIT_TESTS=1 ./test.sh
API_URL=http://localhost:18080/api/v1 bash scripts/run-e2e-integration.sh
```

## License / notice

Demonstration and simulation platform. Do not use example secrets outside a private local environment.
