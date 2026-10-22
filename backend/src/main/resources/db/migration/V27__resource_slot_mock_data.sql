-- 演示用资源目录：30 个目的地 × 5 类资源（酒店/机票/门票/租车/餐饮）。
-- 供应商名统一带「演示」标记，前端据此展示，真实 API 接入后整批禁用即可。
WITH dest(destination, slug, hotel_min, hotel_max, flight_min, flight_max,
          ticket_min, ticket_max, car_min, car_max, dining_min, dining_max) AS (
    VALUES
    ('北京', 'beijing', 500, 1300, 700, 1500, 60, 120, 250, 450, 120, 400),
    ('上海', 'shanghai', 600, 1500, 600, 1400, 180, 350, 300, 500, 150, 450),
    ('西安', 'xian', 300, 700, 600, 1200, 120, 240, 220, 380, 60, 200),
    ('三亚', 'sanya', 900, 2600, 1100, 2400, 120, 260, 300, 600, 150, 500),
    ('杭州', 'hangzhou', 450, 1100, 700, 1500, 60, 120, 260, 450, 100, 300),
    ('大理', 'dali', 260, 600, 900, 1800, 100, 200, 240, 420, 60, 180),
    ('重庆', 'chongqing', 300, 800, 700, 1500, 80, 160, 200, 350, 80, 280),
    ('桂林', 'guilin', 260, 650, 800, 1600, 180, 320, 230, 400, 60, 180),
    ('厦门', 'xiamen', 400, 950, 700, 1500, 100, 200, 250, 420, 90, 260),
    ('青岛', 'qingdao', 380, 900, 650, 1400, 80, 160, 240, 400, 100, 300),
    ('香港', 'hongkong', 900, 2400, 1200, 2600, 380, 680, 400, 700, 150, 500),
    ('曼谷', 'bangkok', 300, 900, 1500, 3200, 200, 450, 150, 300, 60, 220),
    ('新加坡', 'singapore', 800, 2000, 1800, 3800, 300, 600, 400, 700, 100, 350),
    ('巴黎', 'paris', 1200, 3200, 4500, 8500, 180, 360, 500, 900, 250, 800),
    ('京都', 'kyoto', 800, 2200, 2500, 5200, 60, 150, 400, 700, 150, 500),
    ('大阪', 'osaka', 700, 1800, 2400, 5000, 400, 800, 350, 650, 100, 350),
    ('丽江', 'lijiang', 300, 750, 1000, 2000, 150, 300, 240, 420, 70, 200),
    ('广州', 'guangzhou', 400, 1000, 650, 1400, 120, 260, 250, 420, 100, 350),
    ('深圳', 'shenzhen', 450, 1100, 650, 1400, 200, 400, 260, 440, 100, 350),
    ('南京', 'nanjing', 350, 850, 650, 1400, 70, 150, 220, 380, 80, 260),
    ('苏州', 'suzhou', 380, 900, 700, 1500, 90, 180, 230, 390, 90, 280),
    ('长沙', 'changsha', 300, 750, 600, 1300, 80, 160, 200, 360, 70, 220),
    ('武汉', 'wuhan', 320, 800, 650, 1400, 60, 130, 200, 360, 70, 220),
    ('哈尔滨', 'harbin', 300, 800, 900, 1900, 200, 400, 220, 390, 80, 250),
    ('昆明', 'kunming', 300, 750, 800, 1700, 100, 200, 220, 380, 60, 200),
    ('张家界', 'zhangjiajie', 260, 650, 900, 1800, 220, 420, 220, 380, 60, 180),
    ('敦煌', 'dunhuang', 260, 620, 1000, 2100, 120, 260, 240, 400, 60, 180),
    ('拉萨', 'lhasa', 320, 780, 1400, 2800, 100, 220, 280, 480, 80, 220),
    ('成都', 'chengdu', 300, 500, 900, 1600, 50, 90, 200, 350, 80, 200),
    ('东京', 'tokyo', 800, 1200, 2200, 3800, 120, 180, 300, 500, 150, 400)
),
spec(item, type, supplier, availability, refund_policy, price_kind) AS (
    VALUES
    (1, 'hotel', 'Booking 演示', 'available', '入住前 7 天可免费取消', 'hotel1'),
    (2, 'hotel', 'Agoda 演示', 'tight', '入住前 3 天可免费取消', 'hotel2'),
    (3, 'flight', '航司直营 演示', 'available', '按舱位规则退改', 'flight'),
    (4, 'ticket', '景区官方 演示', 'available', '未使用可退', 'ticket1'),
    (5, 'ticket', 'Klook 演示', 'available', '未使用可退', 'ticket2'),
    (6, 'car', '租车平台 演示', 'available', '取车前 24 小时可取消', 'car'),
    (7, 'dining', '美食平台 演示', 'available', '以商家规则为准', 'dining1'),
    (8, 'dining', '大众点评 演示', 'available', '以商家规则为准', 'dining2')
)
INSERT INTO t_resource_slot
    (id, type, destination, supplier, price_min, price_max, currency, availability,
     refund_policy, target_url, affiliate_params, freshness, enabled, sort_weight)
SELECT
    'r8' || lpad((row_number() OVER (ORDER BY d.destination, s.item))::text, 5, '0'),
    s.type,
    d.destination,
    s.supplier,
    CASE s.price_kind
        WHEN 'hotel1' THEN d.hotel_min
        WHEN 'hotel2' THEN round(d.hotel_min * 0.4)
        WHEN 'flight' THEN d.flight_min
        WHEN 'ticket1' THEN d.ticket_min
        WHEN 'ticket2' THEN round(d.ticket_min * 1.8)
        WHEN 'car' THEN d.car_min
        WHEN 'dining1' THEN d.dining_min
        ELSE round(d.dining_min * 0.55)
    END::numeric(12, 2),
    CASE s.price_kind
        WHEN 'hotel1' THEN d.hotel_max
        WHEN 'hotel2' THEN round(d.hotel_max * 0.65)
        WHEN 'flight' THEN d.flight_max
        WHEN 'ticket1' THEN d.ticket_max
        WHEN 'ticket2' THEN round(d.ticket_max * 2.4)
        WHEN 'car' THEN d.car_max
        WHEN 'dining1' THEN d.dining_max
        ELSE round(d.dining_max * 0.75)
    END::numeric(12, 2),
    'CNY',
    s.availability,
    s.refund_policy,
    'https://example.com/partner/' || s.type || '/' || d.slug || '-' || s.item,
    '{"utm_source":"travel-platform","aid":"demo"}'::jsonb,
    now(),
    TRUE,
    100 - s.item * 5
FROM dest d
CROSS JOIN spec s;

-- 通用目录：目的地没有专属资源时由 ResourceService 回退到这里，保证演示时永远有可绑定项。
INSERT INTO t_resource_slot
    (id, type, destination, supplier, price_min, price_max, currency, availability,
     refund_policy, target_url, affiliate_params, freshness, enabled, sort_weight)
VALUES
    ('r89001', 'hotel', '*', 'Booking 通用演示', 200, 800, 'CNY', 'available',
     '入住前 7 天可免费取消', 'https://example.com/partner/hotel/generic-1',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 100),
    ('r89002', 'hotel', '*', 'Agoda 通用演示', 120, 300, 'CNY', 'tight',
     '入住前 3 天可免费取消', 'https://example.com/partner/hotel/generic-2',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 95),
    ('r89003', 'flight', '*', '航司直营 通用演示', 500, 2500, 'CNY', 'available',
     '按舱位规则退改', 'https://example.com/partner/flight/generic',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 90),
    ('r89004', 'ticket', '*', '景区官方 通用演示', 50, 300, 'CNY', 'available',
     '未使用可退', 'https://example.com/partner/ticket/generic-1',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 85),
    ('r89005', 'ticket', '*', 'Klook 通用演示', 120, 600, 'CNY', 'available',
     '未使用可退', 'https://example.com/partner/ticket/generic-2',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 80),
    ('r89006', 'car', '*', '租车平台 通用演示', 180, 500, 'CNY', 'available',
     '取车前 24 小时可取消', 'https://example.com/partner/car/generic',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 75),
    ('r89007', 'dining', '*', '美食平台 通用演示', 50, 250, 'CNY', 'available',
     '以商家规则为准', 'https://example.com/partner/dining/generic-1',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 70),
    ('r89008', 'dining', '*', '大众点评 通用演示', 30, 120, 'CNY', 'available',
     '以商家规则为准', 'https://example.com/partner/dining/generic-2',
     '{"utm_source":"travel-platform","aid":"demo"}', now(), TRUE, 65);
