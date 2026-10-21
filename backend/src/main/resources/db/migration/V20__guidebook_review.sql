-- V20：FR-11 复盘评价。
-- 同一用户对同一本攻略只有一条当前评价；重复提交走更新。
CREATE TABLE t_guidebook_review (
    id                    BIGSERIAL    PRIMARY KEY,
    guidebook_id          BIGINT       NOT NULL,
    user_id               BIGINT       NOT NULL,

    overall_score         SMALLINT     NOT NULL,
    ai_accuracy_score     SMALLINT,
    node_execution_score  SMALLINT,
    content               VARCHAR(1000),
    images                JSONB,

    executed_count        INTEGER      NOT NULL DEFAULT 0,
    skipped_count         INTEGER      NOT NULL DEFAULT 0,
    rescheduled_count     INTEGER      NOT NULL DEFAULT 0,
    untouched_count       INTEGER      NOT NULL DEFAULT 0,
    total_count           INTEGER      NOT NULL DEFAULT 0,

    ai_summary            TEXT,
    summary_source        VARCHAR(16),
    profile_suggestion    JSONB,
    iteration_status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    profile_version_after INTEGER,
    decided_at            TIMESTAMP,

    create_time           TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time           TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted               INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT ck_review_overall_score
        CHECK (overall_score BETWEEN 1 AND 5),
    CONSTRAINT ck_review_ai_accuracy_score
        CHECK (ai_accuracy_score IS NULL OR ai_accuracy_score BETWEEN 1 AND 5),
    CONSTRAINT ck_review_node_execution_score
        CHECK (node_execution_score IS NULL OR node_execution_score BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uq_review_guidebook_user
    ON t_guidebook_review (guidebook_id, user_id)
    WHERE deleted = 0;

CREATE INDEX idx_review_user
    ON t_guidebook_review (user_id, id DESC);
