-- V19：FR-10 出行期调整建议。
-- operations 为空表示提示类建议，接受后不改行程。
CREATE TABLE t_trip_suggestion (
    id              BIGSERIAL    PRIMARY KEY,
    guidebook_id    BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    suggestion_type VARCHAR(32)  NOT NULL,
    dedupe_key      VARCHAR(255) NOT NULL,
    reason          TEXT,
    action          TEXT,
    operations      JSONB,
    alternatives    JSONB,
    evidence        JSONB,
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
    decided_at      TIMESTAMP,
    expires_at      TIMESTAMP,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted         INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_trip_suggestion_dedupe
    ON t_trip_suggestion (guidebook_id, dedupe_key)
    WHERE deleted = 0;

CREATE INDEX idx_trip_suggestion_pending
    ON t_trip_suggestion (guidebook_id, status, id DESC);
