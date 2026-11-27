# Five-Agent Demonstration Guide

Presenter guide for the AI Digital Agent Banking & Float Management platform. Every balance, advance, commission and fraud alert is produced through the same Spring Boot business services used in normal operations — nothing is hard-coded into the ledger.

## Quick start (before every presentation)

1. Ensure Postgres (`55432`), AI service (`:8000`) and Spring Boot API (`:18080`) are running.
2. Sign in to the **web portal** as `admin` / `Demo@2026!`.
3. Open **Admin → Simulation Control Centre** (or **Demo Reset**).
4. Click **Reset entire demonstration**, type `RESET`, confirm.
5. Wait for the seed summary (5 agents, historical transactions, 1 fraud investigation).
6. Sign out and re-login — reset invalidates all sessions.

Reset is also available at `POST /api/v1/admin/demo/reset` with body `{ "confirm": "RESET" }`.

---

## Login credentials

Password for **all** demo accounts: `Demo@2026!`

### Field agents (mobile app)

| Persona | Username | Code | E-float | Cash | Credit | Narrative |
|---------|----------|------|---------|------|--------|-----------|
| **John** | `agent1` | AGT-001 | $1,000 | $500 | **850** | Excellent repayment, low fraud risk |
| **Mary** | `agent2` | AGT-002 | $2,000 | $800 | **760** | Good history; surplus float for peers |
| **Peter** | `agent3` | AGT-003 | $100 | $300 | **650** | Moderate activity; float shortage |
| **Sarah** | `agent4` | AGT-004 | $50 | $200 | **420** | Prior advance default; manual review |
| **David** | `agent5` | AGT-005 | $800 | $400 | **280** | Suspicious history; high fraud risk |

### Staff (web portal)

| Role | Username | Display name |
|------|----------|--------------|
| Administrator | `admin` | Alex Admin |
| Area manager | `manager` | Patricia Manager |
| Risk officer | `risk` | Daniel Risk |
| Finance officer | `finance` | Grace Finance |

---

## Demonstration geography

All five agents sit in **Harare Metropolitan (`HRE-METRO`)**:

| Agent | Business | Location |
|-------|----------|----------|
| John | John's First Street Kiosk | First Street Mall, Harare CBD |
| Mary | Mary's Makoni Market Agency | Makoni Market, Chitungwiza |
| Peter | Peter's Highfield Remittances | Machipisa Shopping Centre, Highfield |
| Sarah | Sarah's Epworth Growth Point Store | Epworth Growth Point, Harare South |
| David | David's Mbare Terminal Services | Mbare Musika Bus Terminus |

A second area (`BYO-METRO`, Bulawayo) exists for location-mismatch simulations.

---

## Seeded history (what you will already see)

After reset, the ledger includes realistic history for:

- Customer deposits, withdrawals and agent-to-customer transfers
- Accrued commissions and at least one commission disbursement batch
- Completed float exchanges (Peter ← Mary) and a **pending** Peter→Mary exchange
- Float replenishments from the area pool
- Float advances, repayments and interest / penalty accrual
- John's full repayment (excellent history)
- Sarah's large advance with only a token repayment (default narrative)
- David's early-morning structuring burst and an open risk investigation
- John's outlet: a habitual small-ticket customer who suddenly withdraws **$1,200** off-hours — left **PENDING_REVIEW** for the risk queue (cash-out is always in-person; flag is amount/off-hours pattern)
- Float demand predictions and demo credit scores for each agent

---

## Suggested demonstration workflow

### 1. Healthy agent (John)

1. Mobile: login `agent1` — show balances ≈ $1,000 e-float / $500 cash and credit **850**.
2. Walk transaction history and repaid advance.
3. Optional: run a normal deposit/withdrawal from the agent app.

### 2. Peer float provider (Mary)

1. Login `agent2` — surplus e-float (~$2,000).
2. Area manager or agents: show completed / pending float exchanges where Mary supplies e-float.
3. Control Centre → **Float exchange requests** to generate more peer proposals.

### 3. Float shortage (Peter)

1. Login `agent3` — e-float ≈ $100; pending exchange asking Mary for float.
2. Control Centre → **Insufficient float** or **Area manager alerts**.
3. Area manager (`manager`): low-liquidity notifications / replenishment.

### 4. Manual credit assessment (Sarah)

1. Login `agent4` — thin float, credit **420**, prior default narrative.
2. Finance (`finance`): open advances — outstanding / overdue position.
3. Control Centre → **Float advance requests** / **Loan default pressure**.
4. Show that new credit requires officer review (`MANUAL_REVIEW`).

### 5. Fraud & risk (sudden large amount + David)

1. Risk (`risk`) → **Flagged transactions** → filter **Pending review**.
2. Open the **$1,200 cash out** at John's kiosk (`AGT-001`): customer normally did small tickets, then a sudden large off-hours withdrawal (in-person; risk is the amount/timing pattern).
3. Login `agent5` — credit **280**, high fraud risk (structuring narrative).
4. Risk: open investigation case on David's structuring pattern.
5. Control Centre → **Suspicious transactions** or **Location mismatch alerts**.

### 6. Finance & commissions

1. Finance portal: trial balance (must balance), commissions, advances.
2. Control Centre → **Accumulate commissions** then **Commission disbursement**.

### 7. Predictions & accelerated day

1. Control Centre → **Float predictions**, **Accelerated activity**, **Normal transactions**.
2. Area manager dashboard: refreshed alerts and agent map in Harare.

---

## Simulation Control Centre (admin)

Path: **Admin → Simulations**

| Section | Actions |
|---------|---------|
| Daily activity | Normal transactions, Customer deposits / withdrawals, Accelerated activity |
| Float stress | Insufficient float, Float exchange requests, Float advance requests, Float predictions, Area manager alerts |
| Risk & fraud | Suspicious transactions, Location mismatch alerts, Loan default pressure |
| Commissions | Accumulate commissions, Commission disbursement |
| Legacy | Morning rush, Market day, Mixed trading, Fraud structuring, Peer rebalancing |

All scenarios call the live transaction, exchange, advance, commission and prediction services. Results are persisted and auditable. Use agent chips to target AGT-001–005; leave none selected to run across the network.

**Reset entire demonstration** on the same page wipes operational tables and re-seeds (same path as Demo Reset).

---

## Apps & ports (local)

| Component | URL / port |
|-----------|------------|
| API | http://localhost:18080 |
| AI service | http://localhost:8000 |
| Web portal (Docker) | http://localhost:8088 |
| Web portal (Vite dev) | http://localhost:5173 |
| Mobile (Expo) | http://localhost:8082 |
| Postgres | localhost:55432 |

Stack lifecycle: `./start.sh`, `./stop.sh`, `./reset-demo.sh`, `./test.sh` (see root `README.md`).

---

## Tips for a clean presentation

- Always **reset** immediately before the audience arrives.
- After reset, re-login every role you will show.
- Prefer Control Centre buttons over ad-hoc API calls so the narrative stays consistent.
- If a scenario fails (e.g. insufficient float on an already-drained agent), that is expected — show the error / blocked path, then reset or replenish via the area manager.
