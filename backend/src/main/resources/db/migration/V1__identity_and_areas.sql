-- Identity, geographical areas and authentication tokens

CREATE TABLE areas (
    id               UUID PRIMARY KEY,
    code             VARCHAR(32)   NOT NULL UNIQUE,
    name             VARCHAR(120)  NOT NULL,
    province         VARCHAR(80)   NOT NULL,
    center_latitude  NUMERIC(10, 6) NOT NULL,
    center_longitude NUMERIC(10, 6) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    username              VARCHAR(64)  NOT NULL UNIQUE,
    email                 VARCHAR(160) NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    full_name             VARCHAR(160) NOT NULL,
    phone_number          VARCHAR(32),
    role                  VARCHAR(32)  NOT NULL CHECK (role IN ('AGENT', 'AREA_MANAGER', 'RISK_OFFICER', 'FINANCE_OFFICER', 'ADMIN')),
    status                VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LOCKED')),
    area_id               UUID REFERENCES areas (id),
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_area_manager_has_area CHECK (role <> 'AREA_MANAGER' OR area_id IS NOT NULL)
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_area ON users (area_id);

CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id),
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL,
    created_by_ip  VARCHAR(64)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE revoked_access_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL
);
