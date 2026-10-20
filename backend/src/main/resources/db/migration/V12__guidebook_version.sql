-- V12: FR-5 攻略编辑 —— 不可变版本快照（append-only）。
-- t_guidebook 仍只保存「当前版本」，前端读取逻辑不变；
-- 每次保存 / AI 调整 / 回溯都向 t_guidebook_version 追加一行完整快照，
-- 历史行只读、永不覆盖。回溯不是回滚：复制旧快照写成一条新版本，版本号继续递增。

-- V8 已把部分唯一索引写死在已执行的迁移里，不能回头修改 V8；
-- 只能在新迁移里先删除再重建（本次要把 editing 纳入「活跃攻略」）。
DROP INDEX IF EXISTS uq_t_guidebook_active_session;

CREATE TABLE t_guidebook_version (
    id                  BIGSERIAL    PRIMARY KEY,
    -- 所属攻略与版本号；同一本攻略的版本号严格递增
    guidebook_id        BIGINT       NOT NULL,
    version             INTEGER      NOT NULL,
    -- 以下字段是完整快照，与 t_guidebook 同名同类型，便于直接复制
    title               VARCHAR(128),
    days                JSONB,       -- 完整日程（每天、每个节点）
    budget_breakdown    JSONB,
    packing_list        JSONB,
    cultural_tips       JSONB,
    pitfall_tips        JSONB,
    -- 变更元信息：摘要给人看，类型用于筛选与审计
    change_summary      TEXT,
    change_type         VARCHAR(16)  NOT NULL, -- generate / manual / adjust / restore
    created_by          BIGINT,                -- 操作人 user_id
    -- BaseEntity 要求的三列：MyBatis-Plus 自动填充时间、逻辑删除标记
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted             INTEGER      NOT NULL DEFAULT 0,
    -- 幂等的最后防线：流水线重复执行也不会写出两条同版本快照
    CONSTRAINT uq_guidebook_version UNIQUE (guidebook_id, version),
    -- 限定变更来源，避免服务层传入拼写错误的类型
    CONSTRAINT ck_guidebook_version_change_type
        CHECK (change_type IN ('generate', 'manual', 'adjust', 'restore'))
);
-- 版本列表查询为 WHERE guidebook_id = ? ORDER BY version DESC；
-- 上面的 UNIQUE (guidebook_id, version) 已提供可反向扫描的 B-tree，
-- 因此不再单独建 guidebook_id 索引。

-- 重建 V8 的部分唯一索引，唯一变化是把 editing 也算作活跃状态。
-- 注意：这里与 GuidebookService.ACTIVE_STATUSES 是两份独立清单，
-- 都漏掉 editing 时，编辑中再次触发生成就会插入第二本活跃攻略。
CREATE UNIQUE INDEX uq_t_guidebook_active_session ON t_guidebook (session_id)
    WHERE session_id IS NOT NULL
      AND deleted = 0
      AND status IN ('generating', 'draft', 'editing', 'confirmed', 'executing', 'completed');
