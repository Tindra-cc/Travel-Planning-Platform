-- V21：FR-12 分享快照。
-- snapshot 创建后不再修改；撤回通过 status 表达，不物理删除。
CREATE TABLE t_guidebook_share (
    id                 BIGSERIAL    PRIMARY KEY,
    share_code         VARCHAR(24)  NOT NULL,
    guidebook_id       BIGINT       NOT NULL,
    owner_id           BIGINT       NOT NULL,
    guidebook_version  INTEGER      NOT NULL,
    channel            VARCHAR(16)  NOT NULL, -- link / qrcode
    title              VARCHAR(200),
    snapshot           JSONB        NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'active', -- active / revoked
    expires_at         TIMESTAMP,
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted            INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_share_code
    ON t_guidebook_share (share_code)
    WHERE deleted = 0;

-- 同一攻略同一渠道最多一个生效分享；旧版本分享会被先撤回
CREATE UNIQUE INDEX uq_share_active_channel
    ON t_guidebook_share (guidebook_id, channel)
    WHERE status = 'active' AND deleted = 0;

CREATE INDEX idx_share_owner
    ON t_guidebook_share (owner_id, id DESC);

ALTER TABLE t_guidebook ADD COLUMN IF NOT EXISTS source_share_id BIGINT;
