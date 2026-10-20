-- V14: FR-6 实时数据接入。
-- 快照随攻略与版本一起落库：用户必须能判断「这条信息是什么时候采集的」。
-- 数据源开关/优先级/缓存时长进配置表，运营可调而不改代码。

ALTER TABLE t_guidebook ADD COLUMN IF NOT EXISTS travel_data JSONB;
ALTER TABLE t_guidebook ADD COLUMN IF NOT EXISTS travel_data_at TIMESTAMP;
ALTER TABLE t_guidebook_version ADD COLUMN IF NOT EXISTS travel_data JSONB;
ALTER TABLE t_guidebook_version ADD COLUMN IF NOT EXISTS travel_data_at TIMESTAMP;

CREATE TABLE t_data_source_config (
    source_id           VARCHAR(64)  PRIMARY KEY,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 数值越小越先执行；仅影响展示与 fetch 顺序
    priority            INTEGER      NOT NULL DEFAULT 100,
    cache_ttl_seconds   INTEGER      NOT NULL DEFAULT 1800,
    config              JSONB,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO t_data_source_config (source_id, enabled, priority, cache_ttl_seconds) VALUES
    ('weather',       TRUE,  10,  1800),
    ('exchange',      TRUE,  20, 21600),
    ('local_news',    TRUE,  30,  3600),
    ('visa',          TRUE,  40, 86400),
    ('flight_price',  TRUE,  50,  1800),
    ('hotel_price',   TRUE,  60,  1800)
ON CONFLICT (source_id) DO NOTHING;

-- 签证政策为人工维护的静态数据；last_verified_at 让用户知道时效。
CREATE TABLE t_visa_policy (
    id                  BIGSERIAL    PRIMARY KEY,
    destination         VARCHAR(64)  NOT NULL,
    nationality         VARCHAR(8)   NOT NULL DEFAULT 'CN',
    policy_summary      TEXT,
    entry_requirements  TEXT,
    last_verified_at    TIMESTAMP,
    source_name         VARCHAR(128),
    source_url          VARCHAR(512),
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted             INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_visa_policy_destination_nationality
    ON t_visa_policy (destination, nationality)
    WHERE deleted = 0;

-- 演示数据：仅用于展示时效标注，出行前必须以官方信息为准。
INSERT INTO t_visa_policy
    (destination, nationality, policy_summary, entry_requirements,
     last_verified_at, source_name, source_url)
VALUES
    ('东京', 'CN', '中国公民赴日旅游通常需办理签证，单次停留不超过 15 天。',
     '护照有效期 6 个月以上；签证申请表；行程单；酒店与机票预订单。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://www.cn.emb-japan.go.jp/'),
    ('大阪', 'CN', '中国公民赴日旅游通常需办理签证，单次停留不超过 15 天。',
     '护照有效期 6 个月以上；签证申请表；行程单；酒店与机票预订单。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://www.cn.emb-japan.go.jp/'),
    ('曼谷', 'CN', '中泰互免签证，普通护照可免签入境停留不超过 30 天。',
     '护照有效期 6 个月以上；返程机票；酒店预订单备查。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://www.mfa.go.th/'),
    ('新加坡', 'CN', '中国公民赴新旅游需办理签证（可申请电子签）。',
     '护照有效期 6 个月以上；签证申请表；行程与住宿证明。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://www.mfa.gov.sg/'),
    ('首尔', 'CN', '中国公民赴韩旅游通常需办理签证。',
     '护照有效期 6 个月以上；签证申请表；行程单；财力证明。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://overseas.mofa.go.kr/'),
    ('巴黎', 'CN', '中国公民赴申根区旅游需办理申根签证。',
     '护照有效期 3 个月以上且超出停留期；行程单；保险；住宿证明。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://france-visas.gouv.fr/'),
    ('纽约', 'CN', '中国公民赴美旅游需办理 B1/B2 签证，需面签。',
     '护照；DS-160 表；面签预约；行程与财力证明。',
     TIMESTAMP '2026-09-01 00:00:00', '演示数据', 'https://www.ustraveldocs.com/')
ON CONFLICT (destination, nationality) WHERE deleted = 0 DO NOTHING;
