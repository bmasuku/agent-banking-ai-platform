# Implementation Roadmap — Incremental 10-Milestone Delivery Plan

## Delivery Philosophy

In accordance with architectural governance, this platform is engineered incrementally across **10 sequential, verifiable milestones**—building the authoritative database, double-entry ledger, backend transaction engine, and explainable AI service *before* constructing the mobile and web client applications.

---

## Milestone 1: Architecture, Database & API Planning (Current)
- **Deliverables**:
  - `docs/SYSTEM_ARCHITECTURE.md`
  - `docs/DATABASE_DESIGN.md`
  - `docs/API_SPECIFICATION.md`
  - `docs/AI_ARCHITECTURE.md`
  - `docs/IMPLEMENTATION_ROADMAP.md`
  - `docs/DEVELOPMENT_CHECKLIST.md`
- **Acceptance Criteria**:
  - Complete alignment with the 4-application + PostgreSQL reference architecture diagram.
  - Balanced double-entry accounting equations defined for all 9 transaction/financial flows.
  - RBAC matrix, REST/WebSocket contracts, and 6 explainable AI module specifications finalized.

---

## Milestone 2: Spring Boot 3 Backend Core, Security & Flyway Schema
- **Deliverables**:
  - Maven Java 21 Spring Boot 3 project (`backend/`) with Spring Security 6 (JWT + `@PreAuthorize`), Spring Data JPA, Flyway, Validation, WebSocket, and SpringDoc OpenAPI (Swagger UI).
  - Flyway migrations `V1`, `V2`, `V3` creating all identity, agent, ledger, transaction, credit, commission, AI, risk, chat, and audit tables.
  - Auth endpoints (`/api/v1/auth/login`, `/refresh`, `/me`), User/Agent management, Audit Trail service, and global exception handling.
- **Acceptance Criteria**:
  - Application boots cleanly on Java 21, executes Flyway migrations, enforces role-based JWT security, and exposes OpenAPI 3 documentation.

---

## Milestone 3: Double-Entry Financial Ledger & Transaction Engine
- **Deliverables**:
  - `LedgerService` enforcing `SUM(DEBIT) == SUM(CREDIT)`, ordered pessimistic row locking, `@Version` optimistic locking, and idempotency key deduplication.
  - `TransactionService` implementing Customer Cash Deposits, Customer Cash Withdrawals, and Agent Money Transfers with dynamic `TariffService` fee & commission calculation.
  - `FloatManagementService` implementing Agent-to-Agent Float Exchanges and Area Manager Float Replenishments + Field Visits.
  - `CreditAndAdvanceService` implementing Credit Score syncing, Float Advance applications/approvals, daily Interest Accrual calculation, and Advance Repayments.
  - `CommissionService` implementing Commission Summaries and Single/Batch Commission Disbursements.
  - `ReportingService` implementing Trial Balance and Income Statement / Treasury KPIs, plus WebSocket STOMP live event publishing.
- **Acceptance Criteria**:
  - Zero balance drift; Trial Balance strictly balances (`totalDebits == totalCredits`) after every operation.

---

## Milestone 4: Python FastAPI AI Intelligence Service
- **Deliverables**:
  - FastAPI microservice (`ai-service/`) with Pydantic v2 contracts and Swagger `/docs`.
  - All 6 explainable AI modules implemented with real `scikit-learn` / `scipy` pipelines and LLM integration:
    1. Fraud Detection (`GradientBoostingClassifier` + typology rules + feature attributions)
    2. Credit Scoring (300–850 calibrated scorecard + factor waterfall)
    3. Float Prediction (24h/7d time-series demand regression + confidence bands + stockout ETA)
    4. Anomaly Detection (`IsolationForest` + multivariate Z-score drift dimensions)
    5. Float Allocation Optimisation (`scipy.optimize.linprog` LP solver + marginal rationale)
    6. AI Conversational Assistant (Role-scoped grounded LLM + deterministic analytical synthesis fallback)
- **Acceptance Criteria**:
  - All 6 endpoints return accurate predictions and non-empty structured explainability payloads within `< 250ms` (for ML/LP endpoints).

---

## Milestone 5: React Native (Expo + TypeScript) Mobile Application
- **Deliverables**:
  - Expo TypeScript app (`mobile-app/`) supporting `AGENT` and `AREA_MANAGER` roles via real REST + WebSocket integration.
  - **`AGENT` Workspace**: Live Wallet Dashboard (E-Float, Cash, Commission, Active Loan), Transaction Execution (Deposit, Withdrawal, Transfer with live backend fee/commission quote), Peer Float Exchange hub, Float Advance & Repayment screen, Commission Disbursement request, 24h AI Float Forecast view, and AI Assistant chat.
  - **`AREA_MANAGER` Workspace**: Regional Agent Live Monitor, Agent Detail & Replenishment execution, Field Visit Cash Verification form, AI Regional Float Allocation Optimiser (preview & one-click execute), and Manager AI Assistant.
- **Acceptance Criteria**:
  - Zero hardcoded balances; every screen reads/writes live backend endpoints and updates dynamically on transactions.

---

## Milestone 6: React Web Application (Vite + TypeScript + Tailwind + Shadcn/UI)
- **Deliverables**:
  - Web Portal (`web-portal/`) with role-tailored workspaces for `RISK_OFFICER`, `FINANCE_OFFICER`, and `ADMIN`:
    - **`RISK_OFFICER`**: Real-time Fraud & Anomaly Alert Queue, Explainability Inspector (feature waterfall charts), Risk Investigation Case Workbench (freeze/unfreeze agent, resolve/escalate), Agent Credit & Risk Matrix, Risk AI Assistant.
    - **`FINANCE_OFFICER`**: Double-Entry General Ledger & Journal Explorer, Live Trial Balance & Income Statement, Float Advance Approval & Interest Schedule Desk, Commission Disbursement Console, Regional Pool Funding & AI Float Forecast/Optimisation Dashboard, Financial Reports.
    - **`ADMIN`**: User & Agent Onboarding, Tariff & Interest Rule Configuration, Simulation Scenario Studio, Immutable Audit Log Viewer, Platform & AI Health Overview, Admin AI Assistant.
- **Acceptance Criteria**:
  - Clean executive banking UI using Tailwind + Shadcn/UI components, live REST + WebSocket updates, and full backend RBAC enforcement.

---

## Milestone 7: End-to-End Cross-Service Integration
- **Deliverables**:
  - Full wiring between `mobile-app`, `web-portal`, `backend`, `ai-service`, and `postgres`.
  - Inline transaction fraud/anomaly screening, live WebSocket STOMP notifications across portals, one-click AI optimisation execution to ledger postings, and grounded AI chat end-to-end.
- **Acceptance Criteria**:
  - An action taken by an `AGENT` on mobile immediately updates ledger balances, triggers AI evaluation, streams to `AREA_MANAGER`, `RISK_OFFICER`, and `FINANCE_OFFICER` views, and maintains Trial Balance integrity.

---

## Milestone 8: Five-Agent & Four-Staff Preconfigured Demonstration
- **Deliverables**:
  - Flyway `V4__seed_demo_data.sql` + Startup Demo Initializer seeding the 5 distinct agent profiles (`AGT-001` through `AGT-005`), 4 staff accounts (`MGR-001`, `RSK-001`, `FIN-001`, `ADM-001`), realistic historical ledger entries, active/overdue float advances, initial forecasts, and open risk investigation case `RISK-2026-0001`.
  - Interactive Simulation Scenario Runner allowing instant replay of market activity (`MORNING_RUSH_WITHDRAWALS`, `MARKET_DAY_DEPOSITS`, `FRAUD_STRUCTURING_ATTACK`, `PEER_REBALANCING_CYCLE`).
- **Acceptance Criteria**:
  - Out-of-the-box login credentials and rich, mathematically consistent ledger history ready for live executive walkthroughs.

---

## Milestone 9: Automated Testing Suite
- **Deliverables**:
  - **Backend (`JUnit 5` + `Spring Boot Test`)**: Double-entry balance invariance tests, concurrency/idempotency tests, deposits/withdrawals/transfers/swaps/advances/interest/commissions tests, and RBAC permission enforcement tests.
  - **AI Service (`pytest`)**: Unit & API contract tests for Fraud Detection, Credit Scoring, Float Forecasting, Anomaly Detection, LP Float Optimisation, and Assistant explainability outputs.
  - **Web / Mobile TypeScript & Build Verification**: Strict `tsc --noEmit`, production Vite bundle build, and API integration tests.
- **Acceptance Criteria**:
  - All automated test suites pass with zero failures.

---

## Milestone 10: Docker Compose Orchestration & Final Documentation
- **Deliverables**:
  - Production-ready multi-stage `Dockerfile`s for `backend`, `ai-service`, and `web-portal`, plus health-checked `docker-compose.yml` with `postgres:16-alpine`.
  - Comprehensive root `README.md` with quick-start guide, architecture overview, demo credentials, API/Swagger links, mobile Expo connection guide, and simulation walkthrough.
- **Acceptance Criteria**:
  - `docker compose up --build` brings up PostgreSQL, AI Service, Spring Boot Backend, and React Web Portal cleanly with all health checks green.
