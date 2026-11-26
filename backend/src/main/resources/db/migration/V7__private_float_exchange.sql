-- Anonymous open float-exchange listings, interests, chat, and meetup verification tokens

ALTER TABLE float_exchanges
    ALTER COLUMN counterparty_agent_id DROP NOT NULL;

ALTER TABLE float_exchanges
    DROP CONSTRAINT IF EXISTS float_exchanges_status_check;

ALTER TABLE float_exchanges
    ADD CONSTRAINT float_exchanges_status_check CHECK (status IN (
        'OPEN', 'PENDING', 'MATCHED', 'VERIFIED', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'
    ));

ALTER TABLE float_exchanges
    ADD COLUMN IF NOT EXISTS anonymity_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS requester_verify_token VARCHAR(12),
    ADD COLUMN IF NOT EXISTS counterparty_verify_token VARCHAR(12),
    ADD COLUMN IF NOT EXISTS requester_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS counterparty_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS matched_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_float_exchanges_anonymity_code
    ON float_exchanges (anonymity_code)
    WHERE anonymity_code IS NOT NULL;

CREATE TABLE float_exchange_interests (
    id                   UUID PRIMARY KEY,
    exchange_id          UUID           NOT NULL REFERENCES float_exchanges (id) ON DELETE CASCADE,
    interested_agent_id  UUID           NOT NULL REFERENCES agents (id),
    anonymity_code       VARCHAR(16)    NOT NULL,
    note                 VARCHAR(255),
    status               VARCHAR(16)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SELECTED', 'DECLINED', 'WITHDRAWN')),
    created_at           TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_exchange_interest UNIQUE (exchange_id, interested_agent_id)
);

CREATE INDEX idx_exchange_interests_exchange ON float_exchange_interests (exchange_id, status, created_at DESC);

CREATE TABLE float_exchange_messages (
    id               UUID PRIMARY KEY,
    exchange_id      UUID           NOT NULL REFERENCES float_exchanges (id) ON DELETE CASCADE,
    sender_agent_id  UUID           NOT NULL REFERENCES agents (id),
    body             VARCHAR(2000)  NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_exchange_messages_exchange ON float_exchange_messages (exchange_id, created_at ASC);
