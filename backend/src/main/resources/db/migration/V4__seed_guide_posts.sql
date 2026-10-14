-- V4: 推荐攻略流演示种子数据（纯演示用，与业务无关）
-- 整表删除即可摘掉：DROP TABLE t_guide_post 重建，或删除本文件并清 flyway_schema_history。
-- 封面走 picsum.photos 外链（同 seed 同图，无需 API key），图片内容与目的地无关，仅作占位。

INSERT INTO t_guide_post
    (title, destination, cover_url, summary, author_name, tags, like_count, view_count, duration_days, budget_level, sort_weight)
VALUES
    ('成都四天三晚｜把火锅和熊猫一次吃够',
     '成都',
     'https://picsum.photos/seed/chengdu-panda/640/420',
     '落地先睡，第二天熊猫基地赶早、下午宽窄巷子，第三天都江堰，收尾春熙路扫街。',
     '阿茶', '["美食","亲子","四天三晚"]'::jsonb, 1284, 20311, 4, 'balanced', 100),

    ('大理的风把我的班味吹散了',
     '大理',
     'https://picsum.photos/seed/dali-lake/640/420',
     '环洱海骑行两天，喜洲古镇住一晚，崇圣寺三塔看日落。适合慢下来的行程。',
     '小舟', '["自然风光","松弛","骑行"]'::jsonb, 862, 15020, 5, 'economy', 98),

    ('东京赏樱五日｜花期跑赢人潮的路线',
     '东京',
     'https://picsum.photos/seed/tokyo-sakura/640/420',
     '上野—千鸟渊—目黑川三段追樱，中间插一天镰仓，末尾筑地吃早市。',
     'Kayo', '["人文","赏樱","城市"]'::jsonb, 2317, 40982, 5, 'comfort', 96),

    ('西安三日｜把十三朝走成一条线',
     '西安',
     'https://picsum.photos/seed/xian-citywall/640/420',
     '兵马俑 + 城墙 + 大唐不夜城，回民街挑三家就够，别排网红店。',
     '老秦', '["历史","美食","三天两晚"]'::jsonb, 1547, 27655, 3, 'economy', 94),

    ('厦门四日｜鼓浪屿之外的厦门更好玩',
     '厦门',
     'https://picsum.photos/seed/xiamen-seaside/640/420',
     '避开发烧的鼓浪屿人流，走沙坡尾、八市、环岛路，海风管够。',
     '阿海', '["自然风光","美食","海边"]'::jsonb, 733, 12089, 4, 'economy', 92),

    ('重庆三日｜8D 城市的正确打开方式',
     '重庆',
     'https://picsum.photos/seed/chongqing-night/640/420',
     '轻轨穿楼、洪崖洞、长江索道一条线，晚上坐南山看夜景，火锅留到宵夜。',
     '幺妹儿', '["夜生活","美食","三天两晚"]'::jsonb, 1988, 33471, 3, 'budget', 90),

    ('青海大环线七日｜一半是湖一半是戈壁',
     '青海',
     'https://picsum.photos/seed/qinghai-loop/640/420',
     '西宁租车出发，青海湖—茶卡—大柴旦—敦煌，自驾七天，注意高反和补给点。',
     '路上的老李', '["自然风光","自驾","探险"]'::jsonb, 1620, 25103, 7, 'balanced', 88),

    ('京都慢走四日｜寺庙与咖啡各一半',
     '京都',
     'https://picsum.photos/seed/kyoto-temple/640/420',
     '清水寺赶早、伏见稻荷赶晚，下午留给哲学之道的小咖啡馆。',
     'Mori', '["人文","历史","摄影"]'::jsonb, 1142, 19877, 4, 'comfort', 86),

    ('三亚亲子五日｜带娃也能有假期感',
     '三亚',
     'https://picsum.photos/seed/sanya-beach/640/420',
     '亚龙湾住带泳池的酒店，中间一天蜈支洲浮潜，其余时间躺平。',
     '豆豆妈妈', '["亲子","海边","度假"]'::jsonb, 908, 16240, 5, 'luxury', 84),

    ('长沙周末两日｜为了吃的也值得来一趟',
     '长沙',
     'https://picsum.photos/seed/changsha-food/640/420',
     '茶颜、米粉、口味虾排满，橘子洲头看江景当消化。',
     '阿辣', '["美食","夜生活","周末"]'::jsonb, 1355, 22076, 2, 'budget', 82),

    ('川西小环线五日｜雪山在窗外',
     '川西',
     'https://picsum.photos/seed/western-sichuan/640/420',
     '成都出发走四姑娘山—丹巴—新都桥，海拔爬升快，前两天别剧烈运动。',
     '石头', '["自然风光","摄影","自驾"]'::jsonb, 1766, 29318, 5, 'balanced', 80),

    ('青岛三日｜啤酒和海风都要',
     '青岛',
     'https://picsum.photos/seed/qingdao-coast/640/420',
     '八大关—栈桥—小麦岛，晚上台东路配原浆，最后一天崂山收尾。',
     '海风', '["海边","美食","三天两晚"]'::jsonb, 612, 10594, 3, 'economy', 78),

    ('丽江+香格里拉六日｜海拔缓慢爬升路线',
     '云南',
     'https://picsum.photos/seed/lijiang-oldtown/640/420',
     '丽江古城适应两天再上香格里拉，普达措和松赞林寺一天一座。',
     '云上', '["自然风光","人文","松弛"]'::jsonb, 1030, 17482, 6, 'balanced', 76),

    ('首尔购物四日｜清单式不走回头路',
     '首尔',
     'https://picsum.photos/seed/seoul-street/640/420',
     '明洞—弘大—狎鸥亭按品类分区逛，把退税门槛一次凑够。',
     '小鹿', '["购物","美食","城市"]'::jsonb, 487, 8631, 4, 'comfort', 74),

    ('桂林阳朔四日｜遇龙河比漓江更安静',
     '桂林',
     'https://picsum.photos/seed/guilin-river/640/420',
     '漓江坐大船看全景，遇龙河竹筏慢漂半天，西街只住不吃。',
     '阿桂', '["自然风光","松弛","摄影"]'::jsonb, 828, 14167, 4, 'economy', 72);
