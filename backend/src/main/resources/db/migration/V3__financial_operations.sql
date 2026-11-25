-- Tariffs, commissions, float advances, exchanges and replenishments

CREATE TABLE tariff_rules (
    id                 UUID PRIMARY KEY,
    transaction_type   VARCHAR(40)    NOT NULL,
    min_amount         NUMERIC(19, 4) NOT NULL CHECK (min_amount >= 0),
    max_amount         NUMERIC(19, 4) NOT NULL,
    fee_percent        NUMERIC(9, 4)  NOT NULL CHECK (fee_percent >= 0),
    fee_flat           NUMERIC(19, 4) NOT NULL CHECK (fee_flat >= 0),
    commission_percent NUMERIC(9, 4)  NOT NULL CHECK (commission_percent >= 0),
    commission_flat    NUMERIC(19, 4) NOT NULL CHECK (commission_flat >= 0),
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMPTZ    NOT NULL,
    updated_by         VARCHAR(64),
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_tariff_range CHECK (max_amount >= min_amount)
);

CREATE INDEX idx_tariff_rules_type ON tariff_rules (transaction_type, active);

CREATE TABLE commission_disbursements (
    id                         UUID PRIMARY KEY,
    reference                  VARCHAR(40)    NOT NULL UNIQUE,
    agent_id                   UUID           NOT NULL REFERENCES agents (id),
    gross_commission           NUMERIC(19, 4) NOT NULL CHECK (gross_commission > 0),
    principal_recovered        NUMERIC(19, 4) NOT NULL CHECK (principal_recovered >= 0),
    interest_recovered         NUMERIC(19, 4) NOT NULL CHECK (interest_recovered >= 0),
    net_commission             NUMERIC(19, 4) NOT NULL CHECK (net_commission >= 0),
    outstanding_advance_before NUMERIC(19, 4) NOT NULL,
    outstanding_advance_after  NUMERIC(19, 4) NOT NULL,
    recovery_order             VARCHAR(16)    NOT NULL,
    max_recovery_percent       NUMERIC(9, 4)  NOT NULL,
    transaction_id             UUID           NOT NULL REFERENCES financial_transactions (id),
    processed_by               UUID REFERENCES users (id),
    created_at                 TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_disbursement_math CHECK (gross_commission = principal_recovered + interest_recovered + net_commission)
);

CREATE INDEX idx_commission_disbursements_agent ON commission_disbursements (agent_id, created_at DESC);

CREATE TABLE commissions (
    id               UUID PRIMARY KEY,
    agent_id         UUID           NOT NULL REFERENCES agents (id),
    transaction_id   UUID           NOT NULL UNIQUE REFERENCES financial_transactions (id),
    transaction_type VARCHAR(40)    NOT NULL,
    base_amount      NUMERIC(19, 4) NOT NULL,
    rate_percent     NUMERIC(9, 4)  NOT NULL,
    flat_amount      NUMERIC(19, 4) NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status           VARCHAR(16)    NOT NULL CHECK (status IN ('ACCRUED', 'DISBURSED')),
    disbursement_id  UUID REFERENCES commission_disbursements (id),
    accrued_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_commissions_agent_status ON commissions (agent_id, status);

CREATE TABLE float_advances (
    id                          UUID PRIMARY KEY,
    reference                   VARCHAR(40)    NOT NULL UNIQUE,
    agent_id                    UUID           NOT NULL REFERENCES agents (id),
    requested_amount            NUMERIC(19, 4) NOT NULL CHECK (requested_amount > 0),
    approved_amount             NUMERIC(19, 4),
    daily_interest_rate         NUMERIC(9, 6),
    term_days                   INTEGER        NOT NULL CHECK (term_days BETWEEN 1 AND 90),
    interest_amount             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    penalty_interest_accrued    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    principal_outstanding       NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (principal_outstanding >= 0),
    interest_outstanding        NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (interest_outstanding >= 0),
    principal_repaid            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    interest_repaid             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status                      VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'OVERDUE', 'REPAID', 'REJECTED')),
    decision_type               VARCHAR(16),
    credit_score_at_request     INTEGER,
    decision_rationale          JSONB          NOT NULL DEFAULT '{}'::jsonb,
    purpose                     VARCHAR(200),
    decision_note               VARCHAR(500),
    decided_by                  UUID REFERENCES users (id),
    requested_at                TIMESTAMPTZ    NOT NULL,
    decided_at                  TIMESTAMPTZ,
    disbursed_at                TIMESTAMPTZ,
    due_date                    DATE,
    last_penalty_accrual_date   DATE,
    closed_at                   TIMESTAMPTZ,
    disbursement_transaction_id UUID REFERENCES financial_transactions (id),
    idempotency_key             VARCHAR(120) UNIQUE,
    version                     BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_float_advances_agent_status ON float_advances (agent_id, status);
CREATE INDEX idx_float_advances_status_due ON float_advances (status, due_date);

CREATE TABLE advance_repayments (
    id               UUID PRIMARY KEY,
    advance_id       UUID           NOT NULL REFERENCES float_advances (id),
    agent_id         UUID           NOT NULL REFERENCES agents (id),
    source           VARCHAR(24)    NOT NULL CHECK (source IN ('AGENT_EFLOAT', 'COMMISSION_DEDUCTION', 'FINANCE_RECORDED')),
    principal_amount NUMERIC(19, 4) NOT NULL CHECK (principal_amount >= 0),
    interest_amount  NUMERIC(19, 4) NOT NULL CHECK (interest_amount >= 0),
    total_amount     NUMERIC(19, 4) NOT NULL CHECK (total_amount > 0),
    transaction_id   UUID           NOT NULL REFERENCES financial_transactions (id),
    disbursement_id  UUID REFERENCES commission_disbursements (id),
    recorded_by      UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_repayment_math CHECK (total_amount = principal_amount + interest_amount)
);

CREATE INDEX idx_advance_repayments_advance ON advance_repayments (advance_id);

CREATE TABLE float_exchanges (
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(40)    NOT NULL UNIQUE,
    requester_agent_id    UUID           NOT NULL REFERENCES agents (id),
    counterparty_agent_id UUID           NOT NULL REFERENCES agents (id),
    direction             VARCHAR(32)    NOT NULL CHECK (direction IN ('EFLOAT_FOR_CASH', 'CASH_FOR_EFLOAT')),
    amount                NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    status                VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    note                  VARCHAR(255),
    response_note         VARCHAR(255),
    transaction_id        UUID REFERENCES financial_transactions (id),
    idempotency_key       VARCHAR(120) UNIQUE,
    requested_at          TIMESTAMPTZ    NOT NULL,
    expires_at            TIMESTAMPTZ    NOT NULL,
    responded_at          TIMESTAMPTZ,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_exchange_distinct_agents CHECK (requester_agent_id <> counterparty_agent_id)
);

CREATE INDEX idx_float_exchanges_requester ON float_exchanges (requester_agent_id, status);
CREATE INDEX idx_float_exchanges_counterparty ON float_exchanges (counterparty_agent_id, status);

CREATE TABLE float_replenishments (
    id                   UUID PRIMARY KEY,
    reference            VARCHAR(40)    NOT NULL UNIQUE,
    agent_id             UUID           NOT NULL REFERENCES agents (id),
    area_id              UUID           NOT NULL REFERENCES areas (id),
    replenishment_type   VARCHAR(16)    NOT NULL CHECK (replenishment_type IN ('EFLOAT', 'CASH')),
    amount               NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    source_account_id    UUID           NOT NULL REFERENCES ledger_accounts (id),
    source_balance_after NUMERIC(19, 4) NOT NULL,
    reason               VARCHAR(255),
    prediction_id        UUID,
    performed_by         UUID           NOT NULL REFERENCES users (id),
    transaction_id       UUID           NOT NULL REFERENCES financial_transactions (id),
    created_at           TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_float_replenishments_area ON float_replenishments (area_id, created_at DESC);
CREATE INDEX idx_float_replenishments_agent ON float_replenishments (agent_id, created_at DESC);
