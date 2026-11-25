-- Agents, customers and the immutable double-entry ledger

CREATE TABLE agents (
    id                   UUID PRIMARY KEY,
    user_id              UUID           NOT NULL UNIQUE REFERENCES users (id),
    agent_code           VARCHAR(24)    NOT NULL UNIQUE,
    business_name        VARCHAR(160)   NOT NULL,
    area_id              UUID           NOT NULL REFERENCES areas (id),
    tier                 VARCHAR(16)    NOT NULL CHECK (tier IN ('PRIME', 'STANDARD', 'WATCHLIST', 'RESTRICTED')),
    status               VARCHAR(16)    NOT NULL CHECK (status IN ('ACTIVE', 'UNDER_REVIEW', 'FROZEN', 'SUSPENDED')),
    risk_level           VARCHAR(16)    NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    credit_score         INTEGER        NOT NULL DEFAULT 600 CHECK (credit_score BETWEEN 300 AND 850),
    credit_limit         NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
    latitude             NUMERIC(10, 6) NOT NULL,
    longitude            NUMERIC(10, 6) NOT NULL,
    address              VARCHAR(200)   NOT NULL,
    min_efloat_threshold NUMERIC(19, 4) NOT NULL CHECK (min_efloat_threshold >= 0),
    min_cash_threshold   NUMERIC(19, 4) NOT NULL CHECK (min_cash_threshold >= 0),
    onboarded_at         TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_agents_area ON agents (area_id);

CREATE TABLE agent_accounts (
    id                      UUID PRIMARY KEY,
    agent_id                UUID           NOT NULL UNIQUE REFERENCES agents (id),
    account_number          VARCHAR(32)    NOT NULL UNIQUE,
    currency                VARCHAR(3)     NOT NULL,
    status                  VARCHAR(16)    NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    daily_transaction_limit NUMERIC(19, 4) NOT NULL CHECK (daily_transaction_limit > 0),
    opened_at               TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE customers (
    id                     UUID PRIMARY KEY,
    customer_number        VARCHAR(32)  NOT NULL UNIQUE,
    full_name              VARCHAR(160) NOT NULL,
    phone_number           VARCHAR(32)  NOT NULL UNIQUE,
    national_id            VARCHAR(48),
    kyc_level              VARCHAR(16)  NOT NULL CHECK (kyc_level IN ('BASIC', 'STANDARD', 'FULL')),
    status                 VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    registered_by_agent_id UUID REFERENCES agents (id),
    created_at             TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_accounts (
    id             UUID PRIMARY KEY,
    code           VARCHAR(64)    NOT NULL UNIQUE,
    name           VARCHAR(160)   NOT NULL,
    category       VARCHAR(16)    NOT NULL CHECK (category IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    normal_side    VARCHAR(8)     NOT NULL CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    balance_type   VARCHAR(32)    NOT NULL,
    owner_type     VARCHAR(16)    NOT NULL CHECK (owner_type IN ('SYSTEM', 'AREA', 'AGENT', 'CUSTOMER')),
    agent_id       UUID REFERENCES agents (id),
    area_id        UUID REFERENCES areas (id),
    customer_id    UUID REFERENCES customers (id),
    currency       VARCHAR(3)     NOT NULL,
    balance        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    allow_negative BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_ledger_balance_non_negative CHECK (allow_negative OR balance >= 0)
);

CREATE UNIQUE INDEX ux_ledger_agent_type ON ledger_accounts (agent_id, balance_type) WHERE agent_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ledger_area_type ON ledger_accounts (area_id, balance_type) WHERE area_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ledger_customer_type ON ledger_accounts (customer_id, balance_type) WHERE customer_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ledger_system_type ON ledger_accounts (balance_type) WHERE owner_type = 'SYSTEM';

CREATE TABLE financial_transactions (
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(40)    NOT NULL UNIQUE,
    idempotency_key       VARCHAR(120)   NOT NULL UNIQUE,
    request_hash          VARCHAR(64),
    type                  VARCHAR(40)    NOT NULL,
    status                VARCHAR(16)    NOT NULL CHECK (status IN ('COMPLETED', 'BLOCKED', 'REVERSED')),
    agent_id              UUID REFERENCES agents (id),
    counterparty_agent_id UUID REFERENCES agents (id),
    customer_id           UUID REFERENCES customers (id),
    area_id               UUID REFERENCES areas (id),
    amount                NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    fee_amount            NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    commission_amount     NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    currency              VARCHAR(3)     NOT NULL,
    description           VARCHAR(255)   NOT NULL,
    failure_reason        VARCHAR(255),
    initiated_by          UUID REFERENCES users (id),
    created_at            TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_fin_tx_agent_created ON financial_transactions (agent_id, created_at DESC);
CREATE INDEX idx_fin_tx_area_created ON financial_transactions (area_id, created_at DESC);
CREATE INDEX idx_fin_tx_customer_created ON financial_transactions (customer_id, created_at DESC);
CREATE INDEX idx_fin_tx_type_created ON financial_transactions (type, created_at DESC);

CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES financial_transactions (id),
    account_id     UUID           NOT NULL REFERENCES ledger_accounts (id),
    side           VARCHAR(8)     NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(19, 4) NOT NULL,
    line_number    INTEGER        NOT NULL,
    memo           VARCHAR(200),
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ux_ledger_entry_line UNIQUE (transaction_id, line_number)
);

CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

-- Ledger entries are append-only.
CREATE FUNCTION fn_reject_mutation() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION '% rows are immutable (attempted %)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();

-- Financial transactions cannot be deleted and their monetary facts cannot change.
CREATE FUNCTION fn_protect_financial_transaction() RETURNS trigger AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'financial_transactions rows cannot be deleted';
    END IF;
    IF NEW.amount <> OLD.amount OR NEW.fee_amount <> OLD.fee_amount OR NEW.commission_amount <> OLD.commission_amount
        OR NEW.type <> OLD.type OR NEW.idempotency_key <> OLD.idempotency_key OR NEW.reference <> OLD.reference
        OR NEW.agent_id IS DISTINCT FROM OLD.agent_id OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'financial_transactions monetary facts are immutable (reference %)', OLD.reference;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_financial_transactions_protect
    BEFORE UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION fn_protect_financial_transaction();

-- Every journal must balance (sum of debits = sum of credits) when the database transaction commits.
CREATE FUNCTION fn_assert_journal_balanced() RETURNS trigger AS
$$
DECLARE
    total_debit  NUMERIC;
    total_credit NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount) FILTER (WHERE side = 'DEBIT'), 0),
           COALESCE(SUM(amount) FILTER (WHERE side = 'CREDIT'), 0)
    INTO total_debit, total_credit
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id;

    IF total_debit <> total_credit THEN
        RAISE EXCEPTION 'Unbalanced journal for transaction %: debits % <> credits %', NEW.transaction_id, total_debit, total_credit;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_assert_journal_balanced();

-- Read model: one row per agent, derived from the agent's ledger accounts (never stored separately).
CREATE VIEW agent_balances AS
SELECT a.id                                                                         AS agent_id,
       COALESCE(SUM(la.balance) FILTER (WHERE la.balance_type = 'EFLOAT'), 0)            AS efloat_balance,
       COALESCE(SUM(la.balance) FILTER (WHERE la.balance_type = 'CASH'), 0)              AS cash_balance,
       COALESCE(SUM(la.balance) FILTER (WHERE la.balance_type = 'COMMISSION_PAYABLE'), 0) AS commission_payable,
       COALESCE(SUM(la.balance) FILTER (WHERE la.balance_type = 'ADVANCE_PRINCIPAL'), 0)  AS advance_principal_outstanding,
       COALESCE(SUM(la.balance) FILTER (WHERE la.balance_type = 'ADVANCE_INTEREST'), 0)   AS advance_interest_outstanding,
       MAX(la.updated_at)                                                           AS updated_at
FROM agents a
         LEFT JOIN ledger_accounts la ON la.agent_id = a.id
GROUP BY a.id;
