-- V23：FR-13 阶段 C 达人发布与市场审核。
-- 发布记录保存商业与审核状态；t_guide_post 是发布时刻的冻结副本。
CREATE TABLE t_guide_publication (
    id                BIGSERIAL    PRIMARY KEY,
    guidebook_id      BIGINT       NOT NULL,
    expert_id         BIGINT       NOT NULL,
    guidebook_version INTEGER      NOT NULL,
    title             VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    cover_url         VARCHAR(500),
    price_cents       INTEGER      NOT NULL DEFAULT 0,
    commission_rate   NUMERIC(4,3) NOT NULL DEFAULT 0.200,
    status            VARCHAR(16)  NOT NULL DEFAULT 'pending_review',
    post_id           BIGINT,
    reviewed_by       BIGINT,
    reviewed_at       TIMESTAMP,
    reject_reason     VARCHAR(500),
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted           INTEGER      NOT NULL DEFAULT 0
);

-- 同一攻略同时最多一条待审/已发布记录；rejected/offline 允许重新发布
CREATE UNIQUE INDEX uq_publication_active
    ON t_guide_publication (guidebook_id)
    WHERE status IN ('pending_review', 'published') AND deleted = 0;

-- 一条市场帖子只能来自一条发布记录
CREATE UNIQUE INDEX uq_publication_post
    ON t_guide_publication (post_id)
    WHERE post_id IS NOT NULL AND deleted = 0;

CREATE INDEX idx_publication_expert
    ON t_guide_publication (expert_id, id DESC);

CREATE INDEX idx_publication_status
    ON t_guide_publication (status, id DESC);

ALTER TABLE t_guide_post
    ADD COLUMN publication_id BIGINT;

CREATE UNIQUE INDEX uq_guide_post_publication
    ON t_guide_post (publication_id)
    WHERE publication_id IS NOT NULL AND deleted = 0;

-- 收益占位：阶段 D 只读展示，未接入支付与结算
CREATE TABLE t_expert_earning (
    id             BIGSERIAL   PRIMARY KEY,
    expert_id      BIGINT      NOT NULL,
    publication_id BIGINT      NOT NULL,
    amount_cents   INTEGER     NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending / settled
    create_time    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_earning_expert
    ON t_expert_earning (expert_id, id DESC);
