-- V3: 推荐攻略内容（市场/社区攻略的消费侧）
-- 表名刻意用 t_guide_post 而非 t_guidebook：后者是 FR-3 用户自己生成的攻略，两者语义不同。

CREATE TABLE t_guide_post (
    id            BIGSERIAL    PRIMARY KEY,
    title         VARCHAR(128) NOT NULL,
    destination   VARCHAR(64)  NOT NULL,
    cover_url     VARCHAR(512),
    summary       VARCHAR(512),
    author_name   VARCHAR(64),
    tags          JSONB,                                     -- ["美食","四天三晚"]
    like_count    INTEGER      NOT NULL DEFAULT 0,
    view_count    INTEGER      NOT NULL DEFAULT 0,
    duration_days INTEGER,
    budget_level  VARCHAR(16),                               -- 复用画像档位穷游/经济/均衡/舒适/奢华
    status        VARCHAR(16)  NOT NULL DEFAULT 'published', -- published / offline
    sort_weight   INTEGER      NOT NULL DEFAULT 0,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted       INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_guide_post_feed ON t_guide_post (status, sort_weight DESC, id DESC);
