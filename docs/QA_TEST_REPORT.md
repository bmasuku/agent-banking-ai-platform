# Platform QA Test Report

**Date:** 2026-11-25  
**Environment:** Local stack — Spring Boot `:18080`, AI Docker `:8000`, Postgres `:55432`, Vite `:5173`  
**Rule:** Results below are from suites that were actually executed in this session. Items not executed are listed under Limitations.

---

## Executive summary

| Suite | Result | Evidence |
|-------|--------|----------|
| Backend Maven (Surefire) | **66 passed**, 0 failed | `mvn test` → surefire reports |
| AI pytest | **22 passed**, 0 failed | `ai-service/.venv/bin/pytest -q` |
| Web Vitest | **7 passed**, 0 failed | `npm test -- --run` |
| Live critical QA | **33 passed**, 0 failed | `scripts/qa-live-critical.mjs` |
| Full-stack E2E | **52 passed**, 0 failed | `scripts/run-e2e-integration.sh` |
| Five-agent demo simulations | **10/10 COMPLETED**, trial balance balanced | Admin simulation API |
| Mobile UI automation | **Not run** | No mobile test harness in repo |

**Bug fixed during QA:** E2E script mapped Peter→`agent2` and Mary→`agent3` (inverted). Corrected to match `DemoDataSeeder` / `DEMO_GUIDE.md`. Added backend integration coverage for `CASH_FOR_EFLOAT` exchanges.

---

## Backend

### Automated (Maven) — 66/66

| Area | Coverage (representative tests) | Result |
|------|----------------------------------|--------|
| Authentication | `AuthorizationIntegrationTest.loginRefreshAndLogoutLifecycle`, `JwtServiceTest` | Pass |
| RBAC | `AuthorizationIntegrationTest.rolesAreEnforcedByTheBackend` | Pass |
| Area isolation | `areaManagerIsRestrictedToOwnArea` | Pass |
| Agent data isolation | `agentsCanOnlySeeTheirOwnData` | Pass |
| Customer txs / float | `FinancialScenariosIntegrationTest` (transfer, deposit, withdrawal, insufficient cash/efloat) | Pass |
| Float exchange | `agentToAgentExchange_bothBalancesUpdateAtomically`, **`cashForEfloatExchange_counterpartySuppliesEfloatAndReceivesCash`** (new) | Pass |
| Float advances + interest | `floatAdvance_efloatIncreasesAndPrincipalAndInterestAreRecorded`, `InterestCalculatorTest` | Pass |
| Commissions / recovery | `CommissionRecoveryIntegrationTest`, `RecoveryAllocatorTest`, tariff tests | Pass |
| Replenishment / ops | `OperationsWorkflowIntegrationTest` | Pass |
| Ledger | `JournalValidatorTest`, scenario journal assertions | Pass |
| Idempotency | `IdempotencyAndConcurrencyIntegrationTest` (replay, key reuse, malformed key) | Pass |
| Concurrency | `concurrentDebitsNeverOverdrawEfloat` | Pass |
| Demo reset | `DemoResetIntegrationTest` | Pass |
| Fraud / credit / forecast units | `RuleBasedFraudScorerTest`, `RuleBasedCreditScorerTest`, `HeuristicFloatForecasterTest` | Pass |

### Live critical API — finance invariants

Executed via `scripts/qa-live-critical.mjs` after demo reset:

- Cannot overdraw e-float → `422 INSUFFICIENT_EFLOAT`, balances unchanged  
- Failed deposit → balances unchanged  
- Duplicate `Idempotency-Key` → second response replayed; single $50 debit  
- Transfer has ≥2 ledger entries; trial balance balanced  
- Exchange reject when counterparty lacks e-float  
- Happy-path peer exchange Peter←Mary deltas correct  
- Area manager replenishment posts  
- Unauthenticated `401`; agent/finance RBAC `403`

---

## AI service

### Automated pytest — 22/22

| Area | Tests | Result |
|------|-------|--------|
| Fraud detection | `tests/test_fraud.py` (clean LOW; far location + extreme amount) | Pass |
| Credit scoring | `tests/test_credit.py` | Pass |
| Float forecasting | `tests/test_forecasting.py` | Pass |
| Anomaly detection | `tests/test_anomaly.py` | Pass |
| Float allocation / optimisation | `tests/test_optimisation.py` | Pass |
| Assistant | `tests/test_assistant.py` | Pass |
| Governance / model metadata | `tests/test_governance.py` | Pass |
| Security (API key) | `tests/test_security.py` | Pass |
| Contracts | `tests/test_contracts.py` | Pass |

### Live AI checks

- Float prediction and credit score responses include **explanations**  
- Seeded scores: John 850, Mary 760, Peter 650, Sarah 420, David 280  
- Fraud clean → `LOW`; geo mismatch → `CRITICAL` with `LOCATION_FAR_FROM_REGISTERED`  
- Missing internal API key → `401`  
- Assistant probe did **not** move ledger balances (HTTP 422 / non-executing reply)

---

## Web portal

| Check | How | Result |
|-------|-----|--------|
| Unit tests | Vitest 7 tests | Pass |
| Risk dashboard / investigations | Live API + E2E | Pass |
| Finance float position, commissions, advances | Live API + E2E disbursement path | Pass |
| Manual credit (finance approve referred advance) | E2E | Pass |
| Commission disbursement + interest recovery | E2E (gross/net/interest/principal) | Pass |
| Admin simulations (19 scenarios listed) | Live API | Pass |
| Browser UI click-through | Not automated this session | See limitations |

---

## Mobile

| Capability | API / E2E coverage | Native UI test |
|------------|--------------------|----------------|
| Agent / area manager login | E2E JWT + roles | Not run |
| Dashboards (role-gated APIs) | RBAC + E2E | Not run |
| Transfer / deposit / withdrawal | Backend + E2E transfer; deposit fail-path live | Not run |
| Float requests / exchange / peers | Live peers + E2E exchange | Not run |
| Float replenishment | E2E area-manager replenish | Not run |
| AI assistant | AI unit + live probe | Not run |
| Field visits | Device-local storage only (by design) | Not run |

There is **no** Jest/Detox/Maestro suite under `mobile-app/` for UI automation.

---

## Critical financial requirements

| Requirement | Evidence | Result |
|-------------|----------|--------|
| Cannot send more float than held | Live + `FinancialScenarios` + E2E Peter overdraw | Pass |
| Cannot exchange more than available | Live short-counterparty accept + new integration test | Pass |
| Failed txs do not change balances | Live deposit fail; E2E Peter fail | Pass |
| Duplicates do not double-move money | Idempotency integration + live | Pass |
| Every movement has ledger entries | Live transfer 4 lines; scenario `assertBalancedJournal` | Pass |
| Concurrent txs do not overdraw | `concurrentDebitsNeverOverdrawEfloat` | Pass |
| Interest not double-counted | `InterestCalculatorTest` + commission recovery E2E fields | Pass (unit + recovery path) |
| Commission deductions correct | E2E disbursement preview/net/interest | Pass |
| Unauthorised access blocked | Auth integration + live | Pass |
| Area managers scoped to area | `areaManagerIsRestrictedToOwnArea` | Pass |

---

## AI requirements

| Requirement | Evidence | Result |
|-------------|----------|--------|
| Every AI result has explanation | Live prediction + credit; fraud factors/reason_codes | Pass (sampled) |
| Fraud vs simulated cases | pytest location/amount cases; E2E David geo + WS alert | Pass |
| Credit vs seeded profiles | Live seed scores match personas | Pass |
| Float prediction error vs future txs | Not measured as MAE/RMSE on holdout | **Not measured** |
| FP/FN rates on labeled set | No labeled production dataset eval | **Not measured** |
| Assistant cannot bypass backend permissions | Internal key required; assistant does not call agent banking APIs to move money | Pass (sampled) |
| Assistant cannot execute financial movements | Live probe; balances intact | Pass (sampled) |

---

## Five-agent demonstration

1. Demo reset → 5 agents, seeded history, 1 investigation  
2. Personas verified at target balances/scores  
3. Control-centre scenarios run to `COMPLETED`:  
   `NORMAL_AGENT_TRANSACTIONS`, `CUSTOMER_DEPOSITS`, `INSUFFICIENT_FLOAT`, `FLOAT_EXCHANGE_REQUESTS`, `SUSPICIOUS_TRANSACTIONS`, `LOCATION_MISMATCH_ALERTS`, `FLOAT_ADVANCE_REQUESTS`, `ACCUMULATE_COMMISSIONS`, `FLOAT_PREDICTIONS`, `AREA_MANAGER_ALERTS`  
4. Trial balance remained **balanced** after simulations  
5. Environment **re-reset** at end of QA for a clean presentation state  

Credentials and walkthrough: [`DEMO_GUIDE.md`](../DEMO_GUIDE.md).

---

## Bugs found and fixed

1. **E2E persona swap (P0 for demo story)** — `web-app/scripts/e2e-integration.mjs` logged Peter as `agent2` and Mary as `agent3`, causing float-exchange accept to debit the wrong agent’s e-float (`INSUFFICIENT_EFLOAT` on AGT-003). Fixed mapping + identity assertions.  
2. **Missing `CASH_FOR_EFLOAT` integration coverage** — added `cashForEfloatExchange_counterpartySuppliesEfloatAndReceivesCash` to `FinancialScenariosIntegrationTest` (happy path + insufficient counterparty).

---

## Remaining limitations

1. **No automated mobile UI tests** — agent/area-manager screens exercised only via shared backend APIs and E2E story, not Expo UI.  
2. **No browser Playwright/Cypress suite** — web portal validated via API/E2E, not full UI navigation.  
3. **Float prediction accuracy** — explanations returned; quantitative forecast error vs subsequent simulated demand was **not** computed.  
4. **Fraud FP/FN on a labeled corpus** — rule/AI unit cases and one geo E2E case only; no ROC/PR evaluation set.  
5. **Field visits** — intentionally device-local; not server-auditable in this build.  
6. **Interest double-count** — covered by calculator unit tests and disbursement recovery E2E; no dedicated multi-day penalty accrual soak beyond seed/penalty scheduler paths.  
7. **Live backend jar** during QA was the M8 demo build; new exchange test is in source and passed under Testcontainers Maven — redeploy jar before relying on that test in the live process classpath.

---

## How to re-run

```bash
# Backend
cd backend && mvn test

# AI
cd ai-service && .venv/bin/pytest -q

# Web unit
cd web-app && npm test -- --run

# Live critical + demo personas
node scripts/qa-live-critical.mjs

# Full E2E (API + WS)
API_URL=http://localhost:18080/api/v1 WS_URL=ws://localhost:18080/ws bash scripts/run-e2e-integration.sh
```
