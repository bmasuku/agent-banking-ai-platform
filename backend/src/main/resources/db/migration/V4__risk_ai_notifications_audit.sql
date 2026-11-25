-- AI evaluations, risk investigations, notifications, audit, simulation and configuration

CREATE TABLE fraud_checks (
    id                    UUID PRIMARY KEY,
    transaction_id        UUID          NOT NULL UNIQUE REFERENCES financial_transactions (id),
    agent_id              UUID          NOT NULL REFERENCES agents (id),
    score                 NUMERIC(6, 4) NOT NULL CHECK (score BETWEEN 0 AND 1),
    risk_level            VARCHAR(16)   NOT NULL,
    decision              VARCHAR(16)   NOT NULL CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    flagged               BOOLEAN       NOT NULL,
    review_status         VARCHAR(24)   NOT NULL,
    reason_codes          JSONB         NOT NULL,
    feature_contributions JSONB         NOT NULL,
    features              JSONB         NOT NULL,
    explanation           TEXT          NOT NULL,
    model_source          VARCHAR(24)   NOT NULL,
    model_version         VARCHAR(64)   NOT NULL,
    reviewed_by           UUID REFERENCES users (id),
    review_note           VARCHAR(500),
    reviewed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_fraud_checks_flagged ON fraud_checks (flagged, review_status, created_at DESC);
CREATE INDEX idx_fraud_checks_agent ON fraud_checks (agent_id, created_at DESC);

CREATE TABLE credit_scores (
    id                     UUID PRIMARY KEY,
    agent_id               UUID           NOT NULL REFERENCES agents (id),
    score                  INTEGER        NOT NULL CHECK (score BETWEEN 300 AND 850),
    risk_band              VARCHAR(16)    NOT NULL,
    probability_of_default NUMERIC(6, 4)  NOT NULL,
    recommended_limit      NUMERIC(19, 4) NOT NULL,
    recommended_daily_rate NUMERIC(9, 6)  NOT NULL,
    factors                JSONB          NOT NULL,
    features               JSONB          NOT NULL,
    explanation            TEXT           NOT NULL,
    model_source           VARCHAR(24)    NOT NULL,
    model_version          VARCHAR(64)    NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_credit_scores_agent ON credit_scores (agent_id, created_at DESC);

CREATE TABLE float_predictions (
    id                        UUID PRIMARY KEY,
    agent_id                  UUID           NOT NULL REFERENCES agents (id),
    horizon_hours             INTEGER        NOT NULL,
    current_efloat            NUMERIC(19, 4) NOT NULL,
    current_cash              NUMERIC(19, 4) NOT NULL,
    projected_efloat          NUMERIC(19, 4) NOT NULL,
    projected_cash            NUMERIC(19, 4) NOT NULL,
    min_projected_efloat      NUMERIC(19, 4) NOT NULL,
    min_projected_cash        NUMERIC(19, 4) NOT NULL,
    predicted_efloat_net_flow NUMERIC(19, 4) NOT NULL,
    predicted_cash_net_flow   NUMERIC(19, 4) NOT NULL,
    hours_to_efloat_stockout  NUMERIC(8, 2),
    hours_to_cash_stockout    NUMERIC(8, 2),
    risk_type                 VARCHAR(24)    NOT NULL,
    recommended_efloat_topup  NUMERIC(19, 4) NOT NULL,
    recommended_cash_topup    NUMERIC(19, 4) NOT NULL,
    confidence                NUMERIC(5, 4)  NOT NULL,
    trajectory                JSONB          NOT NULL,
    drivers                   JSONB          NOT NULL,
    explanation               TEXT           NOT NULL,
    model_source              VARCHAR(24)    NOT NULL,
    model_version             VARCHAR(64)    NOT NULL,
    created_at                TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_float_predictions_agent ON float_predictions (agent_id, created_at DESC);

CREATE SEQUENCE investigation_case_seq START 1;

CREATE TABLE investigations (
    id               UUID PRIMARY KEY,
    case_number      VARCHAR(32)  NOT NULL UNIQUE,
    agent_id         UUID         NOT NULL REFERENCES agents (id),
    fraud_check_id   UUID REFERENCES fraud_checks (id),
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    severity         VARCHAR(16)  NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED', 'CLOSED')),
    outcome          VARCHAR(32),
    actions_taken    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    resolution_notes TEXT,
    assigned_to      UUID REFERENCES users (id),
    opened_by        UUID REFERENCES users (id),
    opened_at        TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    closed_at        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_investigations_status ON investigations (status, severity);
CREATE INDEX idx_investigations_agent ON investigations (agent_id);

CREATE TABLE investigation_notes (
    id               UUID PRIMARY KEY,
    investigation_id UUID        NOT NULL REFERENCES investigations (id),
    author_id        UUID REFERENCES users (id),
    note             TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_investigation_notes_case ON investigation_notes (investigation_id, created_at);

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID         NOT NULL REFERENCES users (id),
    type         VARCHAR(40)  NOT NULL,
    severity     VARCHAR(16)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    message      TEXT         NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    VARCHAR(64),
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY,
    actor_id       UUID,
    actor_username VARCHAR(64) NOT NULL,
    actor_role     VARCHAR(32) NOT NULL,
    action         VARCHAR(64) NOT NULL,
    entity_type    VARCHAR(64) NOT NULL,
    entity_id      VARCHAR(64),
    details        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ip_address     VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();

CREATE TABLE simulation_runs (
    id                     UUID PRIMARY KEY,
    scenario               VARCHAR(40) NOT NULL,
    status                 VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    parameters             JSONB       NOT NULL,
    transactions_attempted INTEGER     NOT NULL DEFAULT 0,
    transactions_completed INTEGER     NOT NULL DEFAULT 0,
    transactions_blocked   INTEGER     NOT NULL DEFAULT 0,
    transactions_failed    INTEGER     NOT NULL DEFAULT 0,
    summary                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    error_message          TEXT,
    started_by             UUID,
    started_at             TIMESTAMPTZ NOT NULL,
    finished_at            TIMESTAMPTZ
);

CREATE TABLE system_settings (
    setting_key   VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    default_value VARCHAR(255) NOT NULL,
    value_type    VARCHAR(16)  NOT NULL,
    category      VARCHAR(40)  NOT NULL,
    description   VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(64)
);
