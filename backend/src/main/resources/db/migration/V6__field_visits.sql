-- Area-manager field visit / check-in log (synced from mobile)

CREATE TABLE field_visits (
    id                      UUID PRIMARY KEY,
    agent_id                UUID           NOT NULL REFERENCES agents (id),
    area_id                 UUID           NOT NULL REFERENCES areas (id),
    area_manager_id         UUID           NOT NULL REFERENCES users (id),
    visit_type              VARCHAR(32)    NOT NULL,
    notes                   TEXT           NOT NULL,
    latitude                NUMERIC(10, 6),
    longitude               NUMERIC(10, 6),
    location_simulated      BOOLEAN        NOT NULL DEFAULT FALSE,
    verified_cash_balance   NUMERIC(19, 4),
    ledger_cash_at_visit    NUMERIC(19, 4),
    discrepancy_amount      NUMERIC(19, 4),
    client_local_id         VARCHAR(64),
    visited_at              TIMESTAMPTZ    NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_field_visit_type CHECK (visit_type IN (
        'ROUTINE_AUDIT', 'REPLENISHMENT', 'RISK_FOLLOWUP', 'ONBOARDING', 'OTHER'
    ))
);

CREATE INDEX idx_field_visits_area_visited ON field_visits (area_id, visited_at DESC);
CREATE INDEX idx_field_visits_agent_visited ON field_visits (agent_id, visited_at DESC);
CREATE INDEX idx_field_visits_manager ON field_visits (area_manager_id, visited_at DESC);
CREATE UNIQUE INDEX uq_field_visits_client_local
    ON field_visits (area_manager_id, client_local_id)
    WHERE client_local_id IS NOT NULL;
