-- V16: 知识条目审核与来源（FR-7）。
-- 审核是数据准入规则：检索只取 approved，UGC 不能直接进入生成上下文。

ALTER TABLE t_knowledge_doc
    ADD COLUMN status        VARCHAR(16) NOT NULL DEFAULT 'approved',
    ADD COLUMN submitted_by  BIGINT,
    ADD COLUMN reviewed_by   BIGINT,
    ADD COLUMN reviewed_at   TIMESTAMP,
    ADD COLUMN source_type   VARCHAR(16),
    ADD COLUMN source_ref_id BIGINT;

COMMENT ON COLUMN t_knowledge_doc.status IS 'pending / approved / rejected';
COMMENT ON COLUMN t_knowledge_doc.source_type IS '来源类型：guide（达人攻略）等；人工录入为空';
COMMENT ON COLUMN t_knowledge_doc.source_ref_id IS '来源对象 ID，与 source_type 组成去重键';

CREATE INDEX idx_t_knowledge_doc_status
    ON t_knowledge_doc (status, destination, deleted);

-- 同一来源对象只允许入库一次；历史数据 source_ref_id 为空，不受约束。
CREATE UNIQUE INDEX uk_t_knowledge_doc_source
    ON t_knowledge_doc (source_type, source_ref_id)
    WHERE source_ref_id IS NOT NULL;

ALTER TABLE t_guide_post
    ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'approved';

COMMENT ON COLUMN t_guide_post.review_status IS 'pending / approved / rejected；存量演示数据默认已审核';
