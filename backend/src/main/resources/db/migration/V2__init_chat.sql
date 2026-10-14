-- V2: 对话会话 / 对话消息（Sprint 1，对应 FR-2 对话式意图采集）

CREATE TABLE t_chat_session (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(128),
    status            VARCHAR(32)  NOT NULL DEFAULT 'collecting', -- collecting / ready_to_generate / generating / failed
    intent            JSONB,
    pending_questions JSONB,
    guidebook_id      BIGINT,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_chat_session_user ON t_chat_session (user_id);

CREATE TABLE t_chat_message (
    id                BIGSERIAL    PRIMARY KEY,
    session_id        BIGINT       NOT NULL,
    role              VARCHAR(16)  NOT NULL,                     -- user / assistant
    content           TEXT,
    pending_questions JSONB,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_t_chat_message_session ON t_chat_message (session_id);
