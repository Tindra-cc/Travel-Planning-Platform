-- V8: 用户生成的攻略（FR-3）。
-- 与 t_guide_post（市场推荐内容）刻意分开：这里是「我的攻略」，属于生成域。

CREATE TABLE t_guidebook (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    session_id          BIGINT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'generating', -- generating / failed / draft / confirmed / executing / completed
    version             INTEGER      NOT NULL DEFAULT 1,
    title               VARCHAR(128),
    intent              JSONB,
    profile_snapshot    JSONB,
    days                JSONB,
    budget_breakdown    JSONB,
    packing_list        JSONB,
    cultural_tips       JSONB,
    pitfall_tips        JSONB,
    degraded_mode       BOOLEAN      NOT NULL DEFAULT FALSE,
    degradation_reasons JSONB,
    current_step        VARCHAR(32),
    progress            INTEGER      NOT NULL DEFAULT 0,
    error_message       TEXT,
    attempt_count       INTEGER      NOT NULL DEFAULT 1,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted             INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_guidebook_user ON t_guidebook (user_id, status, id DESC);
CREATE INDEX idx_t_guidebook_session ON t_guidebook (session_id);
-- 同一会话最多一本活跃攻略；failed 后重试复用原行，因此不参与唯一约束。
CREATE UNIQUE INDEX uq_t_guidebook_active_session ON t_guidebook (session_id)
    WHERE session_id IS NOT NULL
      AND deleted = 0
      AND status IN ('generating', 'draft', 'confirmed', 'executing', 'completed');
