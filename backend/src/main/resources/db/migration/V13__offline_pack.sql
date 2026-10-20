-- V13: FR-9 离线攻略包。
-- 离线包是异步产物：任务行记录状态、绑定生成时的攻略版本与文件位置；
-- 文件写到本地目录（compose 用命名卷持久化），数据库只存相对文件名。

CREATE TABLE t_offline_pack (
    id                  BIGSERIAL    PRIMARY KEY,
    guidebook_id        BIGINT       NOT NULL,
    -- 生成时的攻略版本快照；编辑后再生成会得到新的任务行
    guidebook_version   INTEGER      NOT NULL,
    user_id             BIGINT       NOT NULL,
    format              VARCHAR(8)   NOT NULL,
    include_maps        BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'processing',
    -- 只存文件名（UUID + 扩展名），不拼接用户输入
    file_path           VARCHAR(255),
    file_size           BIGINT,
    error_message       TEXT,
    expires_at          TIMESTAMP,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted             INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_offline_pack_format CHECK (format IN ('pdf', 'html')),
    CONSTRAINT ck_offline_pack_status CHECK (status IN ('processing', 'completed', 'failed'))
);

CREATE INDEX idx_offline_pack_guidebook ON t_offline_pack (guidebook_id, guidebook_version, format);
CREATE INDEX idx_offline_pack_user ON t_offline_pack (user_id, id DESC);
-- 启动恢复扫描：processing 且 started_at 超时的任务
CREATE INDEX idx_offline_pack_status ON t_offline_pack (status, started_at);
