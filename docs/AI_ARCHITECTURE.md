# AI Architecture — Explainable Financial & Risk Intelligence Service

## 1. Overview & Architectural Role

The **AI Intelligence Service (`ai-service/`)** is a dedicated Python 3.11+ **FastAPI** application encapsulating all six intelligence modules depicted in the platform architecture:

1. **Fraud Detection**
2. **Credit Scoring**
3. **Float Prediction**
4. **Anomaly Detection**
5. **Float Optimisation**
6. **AI Conversational Assistant**

### Core Engineering Guarantees
- **No Financial Mutation**: The AI service is stateless with respect to balances—it never mutates ledger tables. Spring Boot extracts authoritative features from PostgreSQL, calls FastAPI, enforces policy guardrails, and persists every AI result and explanation.
- **Deterministic Reproducible Model Training on Startup**: On container/service startup (or build time), calibrated `scikit-learn` pipelines are trained/loaded from domain-realistic synthetic agent banking distributions with fixed random seeds (`random_state=42`) and versioned metadata (`MODEL_REGISTRY`), guaranteeing real `scikit-learn` inference out of the box without brittle binary pickle mismatches across Python versions.
- **Mandatory Explainability Contract**: Every AI response includes a standardized `ExplainabilityPayload` containing feature-level contribution weights, directionality (`INCREASES_RISK` / `REDUCES_RISK` / `DRIVES_DEMAND`), confidence scores, and plain-English explanations suitable for both field agents and risk/finance officers.

---

## 2. Detailed Design of the Six AI Sub-Modules

### 2.1 Module 1 — Fraud Detection (`fraud_detector.py`)
- **Objective**: Estimate real-time probability $P(\text{fraud}) \in [0, 1]$ before a transaction commits.
- **Algorithm**: Hybrid ensemble combining a calibrated `GradientBoostingClassifier` (`scikit-learn`) with deterministic domain typologies (split-transaction structuring, nocturnal velocity bursts, circular peer-swap loops, dormant-to-burst spikes).
- **Input Features ($x \in \mathbb{R}^{10}$)**:
  1. `amount_to_avg_ratio`: Transaction amount divided by agent's 30-day mean ticket size.
  2. `tx_count_1h`: Agent's transaction count in the trailing 60 minutes.
  3. `tx_volume_1h_ratio`: Trailing 1h volume vs daily average volume.
  4. `near_threshold_flag`: Proximity within 5% below reporting/review thresholds (e.g., `$475–$499.99` below `$500`, or `$950–$999.99` below `$1,000`).
  5. `off_hours_indicator`: Transaction occurring outside normal operating window (`21:00–05:30`).
  6. `same_customer_repeat_1h`: Repeated transactions with identical customer phone/ID within 1 hour.
  7. `round_trip_swap_indicator`: Rapid back-and-forth float exchange between the same agent pair.
  8. `agent_historical_risk_score`: Normalised historical risk score of the agent.
  9. `cash_to_efloat_imbalance`: Extreme skew in wallet ratio prior to transaction.
  10. `new_counterparty_burst`: Distinct new recipients in trailing 30 minutes.
- **Explainability Method**: Per-instance log-odds / local perturbation contribution $\Delta_k = f(x) - f(x_{\setminus k})$ comparing the instance feature value against the healthy baseline median, ranked by `|contribution|`, paired with explicit typology reason codes (`STRUCTURING_NEAR_THRESHOLD`, `OFF_HOURS_VELOCITY_SPIKE`, `RAPID_CUSTOMER_REPEAT`, `EXTREME_TICKET_DEVIATION`).
- **Decision Thresholds**:
  - `< 0.45`: `ALLOW` (`LOW`)
  - `0.45 – 0.74`: `ALLOW` + flag `REVIEW` (`MEDIUM`)
  - `0.75 – 0.91`: `REVIEW` + open/append Risk Alert (`HIGH`)
  - `>= 0.92`: `BLOCK` transaction automatically + raise `CRITICAL` alert (`CRITICAL`)

### 2.2 Module 2 — Credit Scoring (`credit_scorer.py`)
- **Objective**: Compute an explainable **300–850 Agent Credit Score**, estimate 30-day default probability $p_d$, assign risk tier, and recommend maximum float advance limit & daily interest rate.
- **Algorithm**: Calibrated `LogisticRegression` + `HistGradientBoostingClassifier` probability blend mapped via standard credit scorecard log-odds scaling:
  $$\text{Score} = \text{clip}\left(\text{round}\left(650 - 55 \cdot \ln\left(\frac{p_d}{1 - p_d}\right)\right), 300, 850\right)$$
- **Input Features**:
  - `repayment_on_time_ratio` (historical float advance repayment punctuality)
  - `avg_daily_tx_volume_30d` (30-day average transaction throughput)
  - `commission_consistency_cv` (coefficient of variation of daily commission income)
  - `overdue_days_90d` (cumulative overdue advance days in past 90 days)
  - `stockout_events_30d` (number of e-float or cash depletion events)
  - `tenure_months` (months active on platform)
  - `audit_discrepancy_ratio` (field visit physical cash discrepancy rate)
  - `fraud_anomaly_incident_rate` (recent confirmed/high-severity risk alerts)
- **Tier & Pricing Schedule**:
  - **750–850 (`LOW` / Prime)**: Max Advance up to `1.2x` 7-day net float turnover (capped at `$10,000`), Daily Interest `0.08%` (`0.000800`).
  - **650–749 (`LOW`–`MEDIUM` / Standard)**: Max Advance up to `0.8x` 7-day turnover (capped at `$5,000`), Daily Interest `0.12%` (`0.001200`).
  - **520–649 (`HIGH` / Watchlist)**: Max Advance up to `$1,500` (requires Finance Officer manual approval), Daily Interest `0.18%` (`0.001800`).
  - **300–519 (`CRITICAL` / Restricted)**: Eligible Advance `$0` (declined until rehabilitation criteria met).
- **Explainability**: Point-contribution waterfall showing exact `+ / -` score points contributed by each factor relative to the 650 base score (e.g., `+68 pts: 98.5% on-time advance repayment`, `-95 pts: Overdue advance balance (4 days past due)`).

### 2.3 Module 3 — Float Prediction (`float_forecaster.py`)
- **Objective**: Forecast hourly (24h) and daily (7d) **E-Float** and **Physical Cash** net flows and ending balances per agent, identifying impending liquidity stockouts before they occur.
- **Algorithm**: `HistGradientBoostingRegressor` (`scikit-learn`) trained with cyclical temporal features ($\sin/\cos$ of hour-of-day and day-of-week), lagged rolling net inflows/outflows (`lag_1h`, `lag_6h`, `lag_24h`, `rolling_mean_7d`), and agent corridor archetype (deposit-heavy vs withdrawal-heavy vs balanced). Quantile/residual variance estimates provide 10th and 90th percentile confidence bands.
- **Outputs & Explainability**:
  - Time-series array of `{ timestamp, projectedEfloat, efloatLower, efloatUpper, projectedCash, cashLower, cashUpper }`.
  - `estimated_hours_to_stockout`: First forecast step where `projectedEfloat < min_efloat_threshold` or `projectedCash < min_cash_threshold`.
  - `recommended_replenishment_efloat` & `recommended_replenishment_cash` sized to restore 48h safety buffer.
  - Feature drivers explaining the forecast (e.g., *"Chitungwiza market corridor exhibits +42% customer cash-in surge between 08:00–13:00, draining E-Float at ~$115/hr"*).

### 2.4 Module 4 — Anomaly Detection (`anomaly_detector.py`)
- **Objective**: Unsupervised detection of novel behavioural drift even when known fraud rules aren't triggered.
- **Algorithm**: `IsolationForest` (`n_estimators=150, contamination=0.08, random_state=42`) paired with multivariate robust Z-score calculation across 7 behavioural dimensions:
  - Ticket size dispersion, deposit-to-withdrawal ratio shift, inter-transaction arrival time compression, nocturnal activity share, peer-exchange frequency, reversal/rejection rate, and customer phone entropy.
- **Explainability**: Reports normalized anomaly score $\in [0, 1]$ plus ranked `drift_dimensions` showing `observed_value`, `agent_baseline_mean`, and `z_score` for each deviating metric.

### 2.5 Module 5 — Float Allocation Optimisation (`float_optimiser.py`)
- **Objective**: Optimally allocate a finite Area Manager regional E-Float Pool ($B_e$) and Cash Vault ($B_c$) across $N$ regional agents to maximize prevented stockout value penalized by agent risk.
- **Mathematical Formulation (Linear Programming via `scipy.optimize.linprog`)**:
  For each agent $i \in \{1, \dots, N\}$, let:
  - $d_{i,e}, d_{i,c} \ge 0$: Forecasted 24h liquidity deficit (amount needed to reach safety buffer + cover net drain).
  - $u_i > 0$: Priority utility weight combining transaction throughput, stockout urgency ($1 / \max(1, \text{hours\_to\_stockout}_i)$), and inverse risk penalty $(1 - 0.5 \cdot r_i)$, with frozen/critical fraud agents capped at $0$ unless overridden.
  - Decision variables $x_{i,e}, x_{i,c} \ge 0$: E-Float and Cash allocated to agent $i$, split into high-priority deficit coverage $x_{i,e}^{(1)} \le d_{i,e}$ and secondary buffer build $x_{i,e}^{(2)} \le b_{i,e}$.
  $$\max_{x} \sum_{i=1}^{N} \left( u_i^{(1)} x_{i,e}^{(1)} + u_i^{(2)} x_{i,e}^{(2)} + v_i^{(1)} x_{i,c}^{(1)} + v_i^{(2)} x_{i,c}^{(2)} \right)$$
  subject to:
  $$\sum_{i=1}^{N} (x_{i,e}^{(1)} + x_{i,e}^{(2)}) \le B_e, \quad \sum_{i=1}^{N} (x_{i,c}^{(1)} + x_{i,c}^{(2)}) \le B_c, \quad 0 \le x_{i,e} \le \text{Cap}_{i,e}$$
- **Explainability**: Returns per-agent allocation, urgency rank, deficit coverage %, LP marginal shadow value, peer-swap opportunity pairing (e.g., recommending `AGT-002` <-> `AGT-003` swap to save regional pool capital!), and plain-language justification.

### 2.6 Module 6 — AI Conversational Assistant (`assistant_service.py`)
- **Architecture**: Grounded Role-Scoped Retrieval & Tool-Context Synthesis with Dual-Mode LLM Execution:
  1. **Role-Scoped Context Assembly (in Spring Boot)**: Before calling FastAPI, Spring Boot queries live PostgreSQL state strictly permitted for the caller's role:
     - `AGENT`: Only their own live balances, commission ledger, advance status, credit score factors, and 24h float forecast.
     - `AREA_MANAGER`: Regional agents' balances, stockout forecasts, field visit history, and latest regional optimisation plan.
     - `RISK_OFFICER`: Open fraud/anomaly alerts, risk cases, agent credit/risk distributions, and suspicious transaction logs.
     - `FINANCE_OFFICER` / `ADMIN`: Trial balance summary, revenue/expense P&L, active float advances, unpaid commissions, and network-wide liquidity KPIs.
  2. **LLM Provider Integration + Deterministic Grounded Analytical Engine**:
     - Supports any OpenAI-compatible chat completions endpoint (`LLM_API_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`, e.g., `gpt-4o-mini` or local Ollama/vLLM) with strict system prompts forbidding invented numbers and requiring citations to the injected context JSON.
     - When `LLM_API_KEY` is not configured (e.g., zero-key local/Docker evaluation), seamlessly executes a **Grounded Analytical Synthesis Engine** that parses intent (liquidity/forecast, credit/advance eligibility, commissions, peer exchange suggestions, fraud/anomaly investigation, ledger/P&L analysis) and composes data-accurate, cited responses directly from the live context payload—ensuring 100% functional, explainable answers in every environment.
  3. **Guardrails**: Cross-role data leakage is architecturally impossible because unauthorized records are never queried or sent into the prompt context by Spring Boot.
