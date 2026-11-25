# API Specification — Spring Boot REST, WebSocket & Internal AI Contracts

> Sections 1, 2 and 4 describe the **implemented** Spring Boot API (Milestones 2–3). The generated
> OpenAPI document is the authoritative contract; this page is the human-readable map.

## 1. API Standards & Documentation

- **Base URL**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `/v3/api-docs` (all), `/v3/api-docs/mobile-agent-area-manager`, `/v3/api-docs/web-risk-finance-admin`
- **AI service (Milestone 4)**: `http://localhost:8000/internal/ai/v1`, Swagger at `http://localhost:8000/docs`
- **Authentication**: `Authorization: Bearer <accessToken>` on every `/api/v1/**` endpoint except
  `POST /auth/login` and `POST /auth/refresh`. Access tokens are HS256 JWTs (15 min); refresh tokens are opaque,
  stored hashed, rotated on every refresh and revocable via logout.
- **Authorization**: enforced in the backend at three layers — URL rules, `@PreAuthorize` role checks, and
  data-scope checks (`AGENT` sees only its own records; `AREA_MANAGER` only agents in its area).
- **Money**: JSON numbers with 2 decimal places, `BigDecimal` end-to-end, currency `USD`.
- **Idempotency**: every money-moving `POST` requires an `Idempotency-Key` header matching
  `^[A-Za-z0-9._:\-]{8,120}$`.
  - First execution → `201 Created`.
  - Same key + same request → original result, `200 OK` with header `Idempotency-Replayed: true`.
  - Same key + different request → `409 IDEMPOTENCY_KEY_REUSED`.
  - Missing key → `400 BAD_REQUEST`; malformed key → `400 INVALID_IDEMPOTENCY_KEY`.
- **Fraud controls**: a transaction blocked by the fraud engine is persisted with status `BLOCKED`
  (no ledger postings) and returned with `422`.
- **Pagination**: `?page=0&size=20` (max 100), response `{content, page, size, totalElements, totalPages}`.
- **Error envelope**:

```json
{
  "timestamp": "2026-11-25T09:33:17.197Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "errorCode": "INSUFFICIENT_EFLOAT",
  "message": "Insufficient e-float wallet balance on account AGT-002-EFLOAT",
  "path": "/api/v1/agent/transactions/deposit",
  "details": { "account": "AGT-002-EFLOAT", "balanceType": "EFLOAT", "available": 505.00, "shortfall": 1495.00 },
  "fieldErrors": null
}
```

---

## 2. Spring Boot REST Endpoints (`/api/v1`)

`[K]` marks endpoints that require an `Idempotency-Key` header.

### 2.1 Authentication (`/auth`) — public / any authenticated user
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/auth/login` | `{username, password}` → `{accessToken, refreshToken, tokenType, expiresIn, refreshExpiresIn, user}`. Locks the account after 5 failed attempts. |
| `POST` | `/auth/refresh` | `{refreshToken}` → new token pair; the old refresh token is revoked (rotation). |
| `POST` | `/auth/logout` | `{refreshToken, allSessions}` → revokes one or all refresh tokens. |
| `GET` | `/auth/me` | Current user, role, `areaId`, `agentId`. |
| `POST` | `/auth/change-password` | `{currentPassword, newPassword}`. |

### 2.2 Agent self-service (`/agent/**`) — `AGENT`
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/agent/me` | Agent profile, area, limits, risk level, credit limit. |
| `GET` | `/agent/balances` | Live ledger balances (e-float, cash, commission payable, advance principal/interest outstanding), thresholds and low-liquidity flags. |
| `GET` | `/agent/statement?type=EFLOAT` | Paginated ledger statement for one balance type. |
| `GET` | `/agent/customers/lookup?phone=` | Customer lookup (`404 CUSTOMER_NOT_FOUND`). |
| `GET` | `/agent/peers` | Active agents in the same area for float exchanges. |
| `POST` | `/agent/transactions/quote` | `{type, amount}` → fee, commission and projected balances. |
| `POST` | `/agent/transactions/transfer` `[K]` | Agent sends money to a customer wallet: **E-float −amount**. |
| `POST` | `/agent/transactions/deposit` `[K]` | Customer gives cash for an electronic transfer: **E-float −amount, Cash +amount**. |
| `POST` | `/agent/transactions/withdrawal` `[K]` | Customer withdraws cash: **E-float +amount, Cash −amount**; customer pays the fee. |
| `GET` | `/agent/transactions` | Own transactions (`type`, `status`, `from`, `to` filters). |
| `GET` | `/agent/transactions/{id}` | Detail with balanced ledger legs and fraud explanation (own or counterparty only). |
| `GET` | `/agent/commissions/summary` · `/agent/commissions` · `/agent/commissions/disbursements` | Accrued and paid commission. |
| `POST` | `/agent/advances` `[K]` | Apply for a float advance `{amount, termDays (1–90), purpose}`; auto-approved within policy, otherwise referred to finance. |
| `GET` | `/agent/advances` · `/agent/advances/{id}` | Own advances with repayment history. |
| `POST` | `/agent/advances/{id}/repayments` `[K]` | Repay from e-float (allocation order is configurable). |
| `POST` | `/agent/exchanges` `[K]` | Propose `{counterpartyAgentId, direction: EFLOAT_FOR_CASH \| CASH_FOR_EFLOAT, amount, note}`. |
| `GET` | `/agent/exchanges` · `/agent/exchanges/{id}` | Exchanges where the agent is a participant. |
| `POST` | `/agent/exchanges/{id}/accept` · `/reject` · `/cancel` | Counterparty accepts (both agents settled atomically in one 4-leg journal) or rejects; requester cancels. |

Customer transaction request body:

```json
{ "amount": 100.00, "customerPhone": "+263772100400", "customerName": "Chiedza Phiri",
  "customerNationalId": "63-2049182-F42", "narration": "School fees" }
```

Response (`201`): `{id, reference, type, status, agentCode, customer, amount, fee, commission, balancesAfter,
fraud: {score, riskLevel, decision, reasonCodes, explanation, modelSource}, ledgerEntries[], replayed}`.

### 2.3 Area manager (`/area-manager/**`) — `AREA_MANAGER`, scoped to the manager's area
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/area-manager/area` | Area profile with e-float/cash pool balances. |
| `GET` | `/area-manager/agents` · `/agents/{id}` · `/agents/{id}/balances` · `/agents/{id}/transactions` | Assigned agents and their live balances/transactions. |
| `GET` | `/area-manager/locations` | Agent GPS locations with liquidity status for the map. |
| `GET` | `/area-manager/predictions` | Latest 24h float prediction per agent (re-computed when older than 30 min). |
| `POST` | `/area-manager/agents/{id}/predictions` | Force a new prediction. |
| `POST` | `/area-manager/agents/{id}/replenishments` `[K]` | `{type: EFLOAT \| CASH, amount, reason, predictionId}`: agent float increases, area pool decreases by the same amount. |
| `GET` | `/area-manager/replenishments` · `/exchanges` · `/transactions` · `/alerts` | Monitoring feeds (exchanges between agents, fraud alerts in the area). |
| `GET` | `/area-manager/reports/area` | Area report: volumes, fees, commission, liquidity, advances, alerts. |

### 2.4 Staff directory & shared queries
| Method | Endpoint | Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/agents` · `/agents/locations` · `/agents/{id}` · `/agents/{id}/balances` · `/agents/{id}/statement` | `RISK_OFFICER`, `FINANCE_OFFICER`, `ADMIN` | Network-wide agent directory. |
| `GET` | `/transactions` · `/transactions/{id}` | `AREA_MANAGER` (own area), `RISK_OFFICER`, `FINANCE_OFFICER`, `ADMIN` | Transaction search (`agentId`, `areaId`, `type`, `status`, `flaggedOnly`, `from`, `to`). |
| `GET` | `/credit-scores/agents/{id}` · `/history` | Anyone allowed to view the agent | Latest / historical explainable credit score. |
| `POST` | `/credit-scores/agents/{id}/evaluate?requestedAmount=` | `RISK_OFFICER`, `FINANCE_OFFICER`, `ADMIN` | Re-score now. |
| `GET` / `POST` | `/predictions/agents/{id}?horizonHours=24` | Anyone allowed to view the agent | Latest / new float prediction with trajectory and drivers. |
| `GET` | `/notifications` · `/notifications/unread-count` | Any | Own notifications. |
| `POST` | `/notifications/{id}/read` · `/notifications/read-all` | Any | Mark read. |
| `GET` | `/audit-logs` | `ADMIN`, `RISK_OFFICER` | Immutable audit trail (`actor`, `action`, `entityType`, `from`, `to`). |

### 2.5 Risk (`/risk/**`) — `RISK_OFFICER`, `ADMIN`
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/risk/dashboard` | Alert counts by level, open investigations, agents under review. |
| `GET` | `/risk/flagged-transactions` | Fraud checks (`flaggedOnly`=true, `riskLevel`, `reviewStatus`, `agentId`, `from`, `to`). |
| `GET` | `/risk/fraud-checks/{id}` | Full explanation: feature contributions, reason codes, features, model source. |
| `POST` | `/risk/fraud-checks/{id}/review` | `{status: CLEARED \| CONFIRMED_FRAUD \| UNDER_INVESTIGATION, note}`. |
| `GET` / `POST` | `/risk/investigations` | List / open a case (`INV-<year>-00001`) against an agent and optional fraud check. |
| `GET` | `/risk/investigations/{id}` | Case with notes and linked checks. |
| `POST` | `/risk/investigations/{id}/assign` · `/notes` · `/status` | Workflow actions. |
| `POST` | `/risk/investigations/{id}/outcome` | `{outcome: CONFIRMED_FRAUD \| POLICY_BREACH \| NO_FRAUD_FOUND \| INSUFFICIENT_EVIDENCE, actions[], resolutionNotes}` closes the case and applies actions (`WARNING_ISSUED`, `AGENT_REINSTATED`, `AGENT_FROZEN`, `AGENT_SUSPENDED`, `DAILY_LIMIT_REDUCED`, `CUSTOMER_BLOCKED`, `ESCALATED_TO_COMPLIANCE`). |
| `GET` | `/risk/agents?areaId=` | Agents with risk level, status and open-case counts. |
| `POST` | `/risk/agents/{id}/status` | `{status: ACTIVE \| UNDER_REVIEW \| SUSPENDED \| FROZEN, reason}`. |

### 2.6 Finance (`/finance/**`) — `FINANCE_OFFICER`, `ADMIN`
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/finance/commissions` | Accrued commission ledger. |
| `GET` | `/finance/commissions/agents/{id}/preview` | Disbursement preview: gross, cap, interest/principal recovered, net, outstanding after. |
| `POST` | `/finance/commissions/agents/{id}/disburse` `[K]` | Disburse one agent. |
| `POST` | `/finance/commissions/disburse-batch` `[K]` | Disburse every eligible agent (per-agent keys `<key>:<agentCode>`; failures reported, not fatal). |
| `GET` | `/finance/disbursements` | Disbursement history. |
| `GET` | `/finance/advances` · `/finance/advances/{id}` | Advance book and detail (credit assessment, explanation). |
| `POST` | `/finance/advances/{id}/approve` · `/reject` | Manual credit decision with note; approval disburses the advance. |
| `POST` | `/finance/advances/{id}/repayments` `[K]` | Record a repayment received outside the e-float wallet. |
| `POST` | `/finance/advances/penalties/run` | Run overdue penalty interest accrual now (also scheduled daily). |
| `POST` | `/finance/treasury/vault-funding` `[K]` | Capitalise the central cash vault (DR vault / CR opening equity). |
| `POST` | `/finance/areas/{areaId}/pool-funding` `[K]` | Fund an area's e-float / cash pools from settlement bank and vault. |
| `GET` | `/finance/replenishments` | All replenishments. |
| `GET` | `/finance/reports/trial-balance` | Account-level trial balance plus journal-level debit/credit totals. |
| `GET` | `/finance/reports/income-statement?from=&to=` | Fee income, interest income, commission expense, net margin. |
| `GET` | `/finance/reports/float-position` | Network and per-area e-float/cash position. |
| `GET` | `/finance/reports/advance-book` | Outstanding principal/interest, overdue ageing. |
| `GET` | `/finance/reports/commissions` | Accrued vs disbursed vs recovered. |
| `GET` | `/finance/reports/transactions?areaId=` | Volume and value by type. |
| `GET` | `/finance/reports/areas/{areaId}` | Area report. |

Commission disbursement response:

```json
{ "id": "…", "agentCode": "AGT-001", "grossCommission": 10.00, "maxRecoveryPercent": 50,
  "recoveryOrder": "INTEREST_FIRST", "interestRecovered": 5.00, "principalRecovered": 0.00,
  "netCommission": 5.00, "outstandingAdvanceBalance": 501.00, "replayed": false }
```

Recovery is capped at `commission.recovery.max_percent` of the gross, never exceeds the outstanding balance,
and the net paid is never negative. Allocation order (`INTEREST_FIRST`, `PRINCIPAL_FIRST`, `PRO_RATA`) is a
runtime setting.

### 2.7 Admin (`/admin/**`) — `ADMIN`
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` / `POST` | `/admin/users` | List / create staff users. |
| `GET` / `PUT` | `/admin/users/{id}` | View / update. |
| `POST` | `/admin/users/{id}/status` · `/reset-password` | Account management. |
| `POST` | `/admin/agents` | Onboard an agent (user + profile + ledger accounts). |
| `PUT` | `/admin/agents/{id}` | Update agent profile and limits. |
| `GET` | `/admin/settings` | Runtime settings (recovery %, order, minimum payout, auto-approval, fraud thresholds, AI toggle…). |
| `PUT` | `/admin/settings/{key}` | `{value}`, validated per key. |
| `GET` / `POST` | `/admin/tariffs` | Fee and commission bands (overlapping bands → `409 TARIFF_OVERLAP`). |
| `PUT` | `/admin/tariffs/{id}` | Update a band. |
| `GET` | `/admin/ai/status` | AI service health and active source (`AI_SERVICE` or `RULES_FALLBACK`). |
| `POST` | `/admin/demo/reset` | `{confirm: "RESET"}` → wipe and reseed the demo dataset through the ledger. |
| `GET` | `/admin/simulations/scenarios` | `MORNING_RUSH_WITHDRAWALS`, `MARKET_DAY_DEPOSITS`, `MIXED_TRADING_HOUR`, `FRAUD_STRUCTURING_ATTACK`, `PEER_REBALANCING_CYCLE`. |
| `POST` | `/admin/simulations` | `{scenario, areaId, agentId, transactionCount (1–200), useAi, randomSeed}` → runs through the real transaction engine. |
| `GET` | `/admin/simulations` · `/admin/simulations/{id}` | Run history and summaries. |

---

## 3. Internal FastAPI Service Contracts (`/internal/ai/v1`)

All endpoints require `X-Internal-Api-Key` and return deterministic JSON schemas validated with Pydantic v2:

1. `POST /internal/ai/v1/fraud/evaluate`
   - **Input**: Transaction payload + engineered agent historical context (1h/24h velocity, average ticket size, std dev, hour-of-day, counterparty repetition, near-threshold indicator, cash/efloat ratio).
   - **Output**: `fraud_probability` (`0..1`), `severity`, `decision` (`ALLOW` | `REVIEW` | `BLOCK`), `reason_codes[]`, `feature_attributions[]`, `summary_explanation`, `model_version`.
2. `POST /internal/ai/v1/anomaly/evaluate`
   - **Input**: Multi-dimensional behavioural vector for agent's rolling window vs 30-day baseline.
   - **Output**: `anomaly_score` (`0..1`), `is_anomalous`, `drift_dimensions[]` (with z-scores), `summary_explanation`, `model_version`.
3. `POST /internal/ai/v1/credit/score`
   - **Input**: 30-day transaction volume, commission stability, stockout frequency, float advance repayment ratio, overdue days, account tenure, anomaly rate, cash audit discrepancy rate.
   - **Output**: `credit_score` (`300..850`), `risk_tier`, `default_probability`, `recommended_max_advance`, `recommended_daily_interest_rate`, `feature_contributions[]`, `summary_rationale`, `model_version`.
4. `POST /internal/ai/v1/float/forecast`
   - **Input**: Historical hourly/daily net e-float & cash flows, current balances, day-of-week, agent profile archetype, horizon (`24` or `168` hours).
   - **Output**: Hourly/daily projected balances with 10th/50th/90th percentile bands, `depletion_risk_type`, `estimated_hours_to_stockout`, `recommended_replenishment_efloat`, `recommended_replenishment_cash`, `explainability`.
5. `POST /internal/ai/v1/float/optimise`
   - **Input**: Regional available E-Float & Cash pools + array of regional agents with current balances, forecasted 24h net drain, risk scores, credit caps, and stockout unit penalty weights.
   - **Output**: Optimal per-agent E-Float & Cash allocations, marginal shadow values, urgency rank, expected stockouts prevented, and per-agent human-readable explanation.
6. `POST /internal/ai/v1/assistant/chat`
   - **Input**: User role, user query, conversation history, and structured live operational snapshot injected by Spring Boot (strictly scoped to what that role is authorised to view).
   - **Output**: Grounded markdown answer, `citations[]` referencing exact entity IDs/metrics, `suggested_followups[]`, and `explainability_metadata`.

When the AI service is disabled, unreachable or cooling down after a failure, the backend uses its built-in
explainable rule models (`RuleBasedFraudScorer`, `RuleBasedCreditScorer`, `HeuristicFloatForecaster`) and
labels every result `modelSource: RULES_FALLBACK`. Platform policy (fraud thresholds, credit band caps) is
always applied on top of model output.

---

## 4. WebSocket Real-Time Specification

- **Endpoints**: STOMP over native WebSocket at `ws://localhost:8080/ws`; SockJS fallback at `/ws-sockjs`.
- **Authentication**: `Authorization: Bearer <accessToken>` header on the STOMP `CONNECT` frame. Every
  `SUBSCRIBE` is authorised server-side.
- **Destinations**:

| Destination | Who may subscribe | Events |
| :--- | :--- | :--- |
| `/topic/agents/{agentId}` | that agent; its area manager; risk, finance, admin | `TRANSACTION_COMPLETED`, `TRANSACTION_BLOCKED`, `REPLENISHMENT_RECEIVED`, `EXCHANGE_*`, `ADVANCE_*`, `COMMISSION_DISBURSED`, `AGENT_STATUS_CHANGED` |
| `/topic/areas/{areaId}` | that area's manager; risk, finance, admin | `AGENT_BALANCE_UPDATED`, `LOW_LIQUIDITY`, `REPLENISHMENT_COMPLETED`, `AREA_POOL_FUNDED`, `EXCHANGE_*`, `AGENT_STATUS_CHANGED` |
| `/topic/risk` | risk, admin | `FRAUD_ALERT`, `FRAUD_ALERT_REVIEWED`, `INVESTIGATION_UPDATED`, `AGENT_STATUS_CHANGED` |
| `/topic/finance` | finance, admin | `ADVANCE_*`, `COMMISSION_DISBURSED`, `AREA_POOL_FUNDED` |
| `/user/queue/notifications` | the authenticated user | `NOTIFICATION` |

- Events are published only **after the database transaction commits**, so clients never see uncommitted state.
- **Envelope**:

```json
{
  "eventId": "f19d82a0-4a11-4098-b91a-12d998123abc",
  "eventType": "TRANSACTION_COMPLETED",
  "timestamp": "2026-11-25T10:20:11Z",
  "payload": { "reference": "TXN-…", "amount": 100.00, "balancesAfter": { "efloat": 8400.00, "cash": 6300.00 } }
}
```
