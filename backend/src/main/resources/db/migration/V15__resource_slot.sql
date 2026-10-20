-- FR-8 资源目录。resourceSlotId 是业务主键（字符串），供节点 JSON 直接引用。
-- 种子数据仅用于演示转链流程，不代表真实库存、价格或合作关系。
CREATE TABLE t_resource_slot (
    id               VARCHAR(32) PRIMARY KEY,
    type             VARCHAR(16) NOT NULL,
    destination      VARCHAR(64) NOT NULL,
    supplier         VARCHAR(64) NOT NULL,
    price_min        NUMERIC(12, 2),
    price_max        NUMERIC(12, 2),
    currency         VARCHAR(8) NOT NULL DEFAULT 'CNY',
    availability     VARCHAR(16) NOT NULL DEFAULT 'available',
    refund_policy    VARCHAR(255),
    target_url       VARCHAR(512) NOT NULL,
    affiliate_params JSONB,
    freshness        TIMESTAMP,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    sort_weight      INT NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT now(),
    update_time      TIMESTAMP NOT NULL DEFAULT now(),
    deleted          SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_resource_slot_search
    ON t_resource_slot (type, destination, enabled, sort_weight DESC);

INSERT INTO t_resource_slot
    (id, type, destination, supplier, price_min, price_max, currency, availability,
     refund_policy, target_url, affiliate_params, freshness, enabled, sort_weight)
VALUES
    ('r70001', 'hotel', '东京', 'Booking 演示', 800, 1200, 'CNY', 'available',
     '入住前 7 天可免费取消', 'https://example.com/partner/hotel/tokyo-1',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 100),
    ('r70002', 'hotel', '东京', '携程 演示', 600, 900, 'CNY', 'tight',
     '入住前 3 天可免费取消', 'https://example.com/partner/hotel/tokyo-2',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 90),
    ('r70003', 'flight', '东京', '航司直营 演示', 2200, 3800, 'CNY', 'available',
     '按舱位规则退改', 'https://example.com/partner/flight/tokyo',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 80),
    ('r70004', 'ticket', '东京', '官方票务 演示', 120, 180, 'CNY', 'available',
     '未使用可退', 'https://example.com/partner/ticket/tokyo',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 70),
    ('r70005', 'dining', '东京', '美食平台 演示', 150, 400, 'CNY', 'available',
     '以商家规则为准', 'https://example.com/partner/dining/tokyo',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 60),
    ('r70006', 'car', '东京', '租车平台 演示', 300, 500, 'CNY', 'link_error',
     '取车前 24 小时可取消', 'https://example.com/partner/car/tokyo',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 50),
    ('r70011', 'hotel', '成都', 'Booking 演示', 300, 500, 'CNY', 'available',
     '入住前 7 天可免费取消', 'https://example.com/partner/hotel/chengdu-1',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 100),
    ('r70012', 'flight', '成都', '航司直营 演示', 900, 1600, 'CNY', 'available',
     '按舱位规则退改', 'https://example.com/partner/flight/chengdu',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 90),
    ('r70013', 'ticket', '成都', '官方票务 演示', 50, 90, 'CNY', 'available',
     '未使用可退', 'https://example.com/partner/ticket/chengdu',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 80),
    ('r70014', 'dining', '成都', '美食平台 演示', 80, 200, 'CNY', 'sold_out',
     '以商家规则为准', 'https://example.com/partner/dining/chengdu',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 70),
    ('r70015', 'car', '成都', '租车平台 演示', 200, 350, 'CNY', 'available',
     '取车前 24 小时可取消', 'https://example.com/partner/car/chengdu',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 60);
