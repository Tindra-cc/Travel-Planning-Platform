-- V9: 目的地知识库（FR-3 Step 1 检索）。
-- 用 pg_trgm 做中文子串召回：不引入 zhparser/Milvus，关键词匹配够用，接口可替换。
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE t_knowledge_doc (
    id          BIGSERIAL    PRIMARY KEY,
    destination VARCHAR(64)  NOT NULL,
    doc_type    VARCHAR(16)  NOT NULL DEFAULT 'guide', -- poi / guide / tip
    title       VARCHAR(128) NOT NULL,
    content     TEXT         NOT NULL,
    tags        JSONB,
    source_name VARCHAR(64),
    source_url  VARCHAR(512),
    sort_weight INTEGER      NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted     INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_t_knowledge_doc_destination ON t_knowledge_doc (destination, deleted);
CREATE INDEX idx_t_knowledge_doc_title_trgm ON t_knowledge_doc USING GIN (title gin_trgm_ops);
CREATE INDEX idx_t_knowledge_doc_content_trgm ON t_knowledge_doc USING GIN (content gin_trgm_ops);
