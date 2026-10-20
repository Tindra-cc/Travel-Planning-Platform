-- V17: 多方案对比与混搭（FR-4）。
-- 对比不是一本攻略的多个视图，而是多本真实攻略 + 一张关系表。

CREATE TABLE t_guidebook_comparison (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    base_guidebook_id BIGINT       NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'processing',
    error_message     TEXT,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted           INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_guidebook_comparison_user ON t_guidebook_comparison (user_id, id DESC);

COMMENT ON TABLE t_guidebook_comparison IS '一次对比生成任务；状态 processing / completed / partial / failed';

ALTER TABLE t_guidebook
    ADD COLUMN comparison_id BIGINT,
    ADD COLUMN variant       VARCHAR(16);

COMMENT ON COLUMN t_guidebook.comparison_id IS '所属对比任务；普通攻略为空';
COMMENT ON COLUMN t_guidebook.variant IS 'budget / balanced / luxury / mixed';

CREATE INDEX idx_t_guidebook_comparison ON t_guidebook (comparison_id);
