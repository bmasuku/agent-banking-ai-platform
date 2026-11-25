# Development Checklist — Agent Banking & Float Management AI Platform

This living checklist tracks completion across all 10 incremental milestones.

## Milestone 1: Architecture & Database Design
- [x] Analyse reference architecture screenshot (`React Native Mobile`, `Spring Boot Backend`, `AI Intelligence Service`, `React Web Portal`, `PostgreSQL Database`)
- [x] Author `docs/SYSTEM_ARCHITECTURE.md`
- [x] Author `docs/DATABASE_DESIGN.md` (Double-entry schema, accounts, Flyway plan, 5-agent seed spec)
- [x] Author `docs/API_SPECIFICATION.md` (REST, WebSockets, Swagger/OpenAPI, Internal AI contracts)
- [x] Author `docs/AI_ARCHITECTURE.md` (6 explainable AI modules + LP formulation + LLM grounding)
- [x] Author `docs/IMPLEMENTATION_ROADMAP.md`
- [x] Create `docs/DEVELOPMENT_CHECKLIST.md`

## Milestone 2: Spring Boot Backend Core (`backend/`)
- [x] Scaffold Maven project with Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, WebSocket, Validation, and SpringDoc OpenAPI
- [x] Create Flyway migrations (`V1` identity/areas, `V2` agents/customers/ledger + immutability triggers, `V3` financial operations, `V4` risk/AI/notifications/audit/settings)
- [x] Implement JWT authentication (login, logout, access + rotating refresh tokens, BCrypt, lockout, change password) & RBAC with area-based data scoping
- [x] Implement User management, Areas, Agent onboarding/profile, Notifications, and Immutable Audit Log services & controllers
- [x] Configure Swagger/OpenAPI (3 groups) & global structured error handling
- [ ] Field visits / cash-till verification — deferred (not in the backend brief; candidate for a later milestone)

## Milestone 3: Financial Ledger & Transaction Engine
- [x] Implement `LedgerService` with `BigDecimal` (`NUMERIC(19,4)`), balanced-journal validation in Java **and** a deferred DB constraint trigger, ordered pessimistic locks, `version` columns, non-negative balance checks, and `Idempotency-Key` support (201 / 200 replay / 409 reuse)
- [x] Implement `TariffService` (banded, configurable fees & commission) and Transaction Quote endpoint
- [x] Implement customer transfer, cash deposit and cash withdrawal flows with inline fraud scoring (allow / review / block)
- [x] Implement Agent-to-Agent Float Exchanges (propose, accept, reject, cancel) settled atomically in one journal
- [x] Implement Area Manager Float Replenishment, Area Pool Funding and Central Vault capitalisation
- [x] Implement credit scoring (AI with explainable rule fallback), float advance application / auto-approval / finance approval, flat interest + overdue penalty accrual, and repayments (agent e-float and finance-recorded)
- [x] Implement Commission accrual & single/batch disbursement with configurable recovery cap and order (never deducts beyond available funds)
- [x] Implement Trial Balance, Income Statement, Float Position, Advance Book, Commission and Area reports + STOMP WebSocket events published after commit
- [ ] General-ledger journal explorer endpoint (`/ledger/journal-entries`) — not yet exposed; statements, transaction detail with ledger legs and trial balance cover current needs

## Milestone 4: AI Intelligence Service (`ai-service/`)
- [x] Scaffold FastAPI service with Pydantic v2 schemas, `X-Internal-Api-Key` auth, governance store, `/health` and model registry
- [x] Module 1: Deterministic fraud typology rules (0–100) with explanations; synthetic evaluation + optional non-serving challenger
- [x] Module 2: Explainable credit scorecard (0–1000) with APPROVED / REJECTED / MANUAL_REVIEW; Java fallback mirrored
- [x] Module 3: Statistical float forecaster (hourly profile, exhaustion, replenishment, area roll-up)
- [x] Module 4: Anomaly detection (robust z / Poisson / Isolation Forest) + peer comparison
- [x] Module 5: Float redistribution LP (`scipy.optimize.linprog` HiGHS) — peer swaps + pool top-ups
- [x] Module 6: Read-only assistant (deterministic router + optional OpenAI tool loop; GET-only Spring Boot tools)
- [x] Docker image, pytest suite (22), training/evaluation scripts
- [x] Backend wiring: V5 credit 0–1000, fraud geo, anomaly/optimisation/assistant/governance proxies, internal-key read-only filter, human-decision feedback; Maven suite green (65 tests)

## Milestone 5: React Native Mobile Application (`mobile-app/`)
- [x] Scaffold Expo + TypeScript mobile project with secure JWT auth & role routing (`AGENT` vs `AREA_MANAGER`) (`mobile-app/`)
- [x] Build `AGENT` screens: Live Wallet Dashboard, Deposit/Withdrawal/Transfer with backend quote, Peer Float Exchange, Float Advances & Repayments, Commissions, 24h AI Forecast, and AI Assistant
- [x] Build `AREA_MANAGER` screens: Regional Agent Live Monitor, Agent Replenishment, Field Visit Cash Verification, AI Float Allocation Optimiser, and Manager AI Assistant
- [x] Connect STOMP/WebSocket live balance & alert updates

## Milestone 6: React Web Portal (`web-app/`)
- [x] Scaffold Vite + React + TypeScript + Tailwind CSS + Shadcn/UI web application with RBAC routing
- [x] Build `RISK_OFFICER` workspace: Live Fraud/Anomaly Queue, Explainability Waterfall Inspector, Risk Investigation Workbench, Credit/Risk Explorer, Risk AI Assistant (web-app)
- [x] Build `FINANCE_OFFICER` workspace: General Ledger & Journal Explorer, Trial Balance & Income Statement, Float Advances & Interest Desk, Commission Disbursements, Regional Liquidity & AI Forecast/Optimisation Dashboard
- [x] Build `ADMIN` workspace: User & Agent Management, Tariff & System Parameters, Simulation Scenario Studio, Immutable Audit Log Explorer, Admin AI Assistant

## Milestone 7: End-to-End Integration
- [x] Wire Spring Boot AI client to FastAPI for inline fraud/anomaly checks, credit scoring, forecasts, LP optimisation, and conversational assistant
- [x] Verify WebSocket real-time propagation across Mobile and Web clients (`useRealtime` + E2E FRAUD_ALERT)
- [x] Verify area-manager replenishment and commission recovery create real ledger-backed balance updates (`scripts/run-e2e-integration.sh` — 47/47)

## Milestone 8: Five-Agent Demonstration & Simulation Engine
- [x] Seed 5 preconfigured agents (`AGT-001`..`AGT-005`) and 4 staff users through the real services (`DemoDataSeeder`, backdated history) + admin demo reset
- [x] Implement backend `SimulationService` (5 scenarios) generating simulated transactions on demand through the real ledger and fraud pipeline

## Milestone 9: Automated Testing
- [x] Write & pass Backend JUnit 5 tests — 64 tests: 34 unit (journal validation, tariffs, recovery allocation, interest, fraud/credit rule models, forecaster, JWT) + 30 Testcontainers integration (6 transaction scenarios, idempotency & concurrency, commission recovery, RBAC/area scoping, workflows, demo reset, OpenAPI)
- [ ] Write & pass AI Service `pytest` suite (all 6 AI endpoints + explainability assertions)
- [ ] Run TypeScript typechecking & production build verification on `web-portal` and `mobile-app`

## Milestone 10: Docker Deployment & Final Documentation
- [ ] Create `Dockerfile`s for `backend`, `ai-service`, and `web-portal`
- [ ] Create `docker-compose.yml` orchestrating `postgres`, `ai-service`, `backend`, and `web-portal` with health checks
- [ ] Author comprehensive root `README.md` with setup, credentials, architecture, and verification steps
- [ ] Validate full stack build & startup end-to-end
