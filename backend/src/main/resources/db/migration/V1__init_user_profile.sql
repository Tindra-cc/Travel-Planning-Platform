-- V1: 用户 / 用户画像 / 出行人档案（Sprint 1，对应 §2 鉴权 与 FR-1 画像）

CREATE TABLE t_user (
    id                   BIGSERIAL    PRIMARY KEY,
    account              VARCHAR(64)  NOT NULL,
    account_type         VARCHAR(16)  NOT NULL,              -- mobile / email
    password_hash        VARCHAR(100) NOT NULL,
    nickname             VARCHAR(64),
    user_level           VARCHAR(8)   NOT NULL DEFAULT 'L1', -- L1 / L2 / L3
    status               INTEGER      NOT NULL DEFAULT 1,    -- 1 正常 0 禁用
    daily_generate_quota INTEGER      NOT NULL DEFAULT 3,
    daily_generate_used  INTEGER      NOT NULL DEFAULT 0,
    profile_completed    BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time          TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time          TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted              INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_t_user_account ON t_user (account);

CREATE TABLE t_user_profile (
    id                       BIGSERIAL    PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    budget_level             VARCHAR(16),
    pace                     VARCHAR(16),
    interest_tags            JSONB,
    dietary_restrictions     VARCHAR(255),
    party_adults             INTEGER,
    party_children           INTEGER,
    party_elderly            INTEGER,
    party_pets               INTEGER,
    special_preferences      JSONB,
    accept_red_eye           BOOLEAN      NOT NULL DEFAULT FALSE,
    transport_preference     VARCHAR(32),
    accommodation_preference VARCHAR(32),
    version                  INTEGER      NOT NULL DEFAULT 0,
    create_time              TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time              TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted                  INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_t_user_profile_user ON t_user_profile (user_id);

CREATE TABLE t_traveler (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    id_type       VARCHAR(16),
    id_number     VARCHAR(64),
    birth_date    DATE,
    special_needs VARCHAR(255),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted       INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_traveler_user ON t_traveler (user_id);
