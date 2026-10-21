--V18 FR-10节点执行状态
--独立于 t_guidebook.days:标记不改变计划内容 也不触发版本号更新

CREATE TABLE t_guidebook_node_status(
    id                     BIGSERIAL       PRIMARY KEY,
    guidebook_id           BIGINT          NOT NULL,
    node_id                VARCHAR(64)     NOT NULL,
    node_day               INTEGER,
    node_name              VARCHAR(128),
    status                 VARCHAR(16)     NOT NULL, --executed / skipped / rescheduled
    marked_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    create_time            TIMESTAMP       NOT NULL DEFAULT NOW(),
    update_time            TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted                INTEGER         NOT NULL DEFAULT 0
);

--一个节点只有一条当前状态；逻辑删除后允许重新插入
CREATE UNIQUE INDEX uq_node_status_guidebook_node
    ON t_guidebook_node_status(guidebook_id, node_id)
    WHERE deleted = 0;

CREATE INDEX idx_node_status_guidebook
    ON t_guidebook_node_status(guidebook_id, status);
