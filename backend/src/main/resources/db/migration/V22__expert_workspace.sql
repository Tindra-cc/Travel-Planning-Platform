-- V22：FR-13 阶段 A 达人工作台底座。
-- 客户档案只存达人维护的信息，不复制画像；授权需客户本人确认；审计只记录访问事实。
CREATE TABLE t_expert_client (
    id             BIGSERIAL   PRIMARY KEY,
    expert_id      BIGINT      NOT NULL,
    name           VARCHAR(80) NOT NULL,
    contact        VARCHAR(120),
    note           VARCHAR(500),
    linked_user_id BIGINT,
    status         VARCHAR(16) NOT NULL DEFAULT 'active', -- active / archived
    create_time    TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted        INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_expert_client_expert
    ON t_expert_client (expert_id, status, id DESC);

CREATE INDEX idx_expert_client_linked
    ON t_expert_client (linked_user_id);

CREATE TABLE t_client_authorization (
    id           BIGSERIAL    PRIMARY KEY,
    client_id    BIGINT       NOT NULL,
    expert_id    BIGINT       NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    scope        JSONB        NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'pending', -- pending / active / revoked
    granted_at   TIMESTAMP,
    revoked_at   TIMESTAMP,
    expires_at   TIMESTAMP,
    evidence     VARCHAR(200),
    create_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted      INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_auth_token_hash
    ON t_client_authorization (token_hash)
    WHERE deleted = 0;

-- 同一客户同时最多一条 active 授权；撤回即时生效
CREATE UNIQUE INDEX uq_auth_client_active
    ON t_client_authorization (client_id)
    WHERE status = 'active' AND deleted = 0;

CREATE INDEX idx_auth_expert
    ON t_client_authorization (expert_id, id DESC);

CREATE TABLE t_client_access_log (
    id            BIGSERIAL   PRIMARY KEY,
    expert_id     BIGINT      NOT NULL,
    client_id     BIGINT      NOT NULL,
    action        VARCHAR(32) NOT NULL,
    scope         VARCHAR(32),
    resource_type VARCHAR(32),
    resource_id   BIGINT,
    ip            VARCHAR(64),
    create_time   TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_client
    ON t_client_access_log (client_id, id DESC);
CREATE INDEX idx_access_expert
    ON t_client_access_log (expert_id, create_time DESC);

ALTER TABLE t_guidebook ADD COLUMN IF NOT EXISTS expert_client_id BIGINT;
