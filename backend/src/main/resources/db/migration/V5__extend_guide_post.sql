-- V5: 推荐攻略详情（多图 + 模块化规划 + 原文外链）
-- 只加列、不改旧列：V3 早已应用，Flyway 校验和写死在 flyway_schema_history，改 V3 会导致校验失败，
-- 所以详情字段一律走新增迁移。

ALTER TABLE t_guide_post
    ADD COLUMN source_url    VARCHAR(512),  -- 原文外链（演示数据指向目的地维基页）
    ADD COLUMN images        JSONB,         -- 图集 URL 数组，首元素与 cover_url 同一 seed
    ADD COLUMN plan_sections JSONB;         -- 模块化规划，见下

COMMENT ON COLUMN t_guide_post.source_url IS '原文外链；为空则前端不渲染「查看原文」按钮';
COMMENT ON COLUMN t_guide_post.images IS '图集外链数组，用于详情页多图切换';
COMMENT ON COLUMN t_guide_post.plan_sections IS
    '模块化规划：[{"type":"stay|food|spot|transport","title":"住宿","items":[{"name","note","price"}]}]，数组顺序即渲染顺序';
