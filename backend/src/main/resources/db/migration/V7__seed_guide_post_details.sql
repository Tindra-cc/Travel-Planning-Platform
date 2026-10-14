-- V7: 推荐攻略「详情」演示种子数据（纯演示用，与业务无关）
-- 给 V4 的 15 条攻略补 source_url / images / plan_sections。
-- 整块可摘：删本文件并清 flyway_schema_history 对应记录即可。
--
-- 两点说明（演示数据的取舍，别当成真数据）：
--   1. images 走 picsum.photos 外链（同 seed 同图，无需 key），**图片内容与目的地无关**；
--      首图与 V4 的 cover_url 同一 seed，保证卡片封面与详情首帧一致。
--   2. source_url 指向目的地中文维基页（真实可达且与目的地相关）——演示数据没有真实原文，
--      用它顶替「查看原文」。正式接入市场攻略时应改为攻略自身的原文地址。

UPDATE t_guide_post g
SET source_url    = v.source_url,
    images        = v.images,
    plan_sections = v.plan_sections
FROM (VALUES
    -- 1 成都
    ('成都四天三晚｜把火锅和熊猫一次吃够',
     'https://zh.wikipedia.org/wiki/成都市',
     '["https://picsum.photos/seed/chengdu-panda/960/640","https://picsum.photos/seed/chengdu-panda-2/960/640","https://picsum.photos/seed/chengdu-panda-3/960/640","https://picsum.photos/seed/chengdu-panda-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"宽窄巷子·隐庐","note":"步行到地铁，出门就是小吃街","price":"¥420/晚"},
         {"name":"春熙路·亚朵S","note":"商圈中心，逛吃都方便","price":"¥560/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"马旺子川小馆","note":"本地人排队，别点网红菜","price":"人均 ¥80"},
         {"name":"青年火锅","note":"牛油锅底，微辣就够","price":"人均 ¥110"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"大熊猫繁育研究基地","note":"7:30 开园就进，10 点后熊猫就回窝","price":"¥55"},
         {"name":"都江堰景区","note":"半天来回，记得带防晒","price":"¥80"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 1/2/3 号线","note":"机场 10 号线转 1 号线，约 50 分钟","price":"¥2 起"},
         {"name":"高铁到都江堰","note":"成都站出发约 30 分钟","price":"¥10"}]}
     ]'::jsonb),

    -- 2 大理
    ('大理的风把我的班味吹散了',
     'https://zh.wikipedia.org/wiki/大理市',
     '["https://picsum.photos/seed/dali-lake/960/640","https://picsum.photos/seed/dali-lake-2/960/640","https://picsum.photos/seed/dali-lake-3/960/640","https://picsum.photos/seed/dali-lake-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"才村码头·洱海民宿","note":"推窗见海，也是骑行起点","price":"¥320/晚"},
         {"name":"喜洲古镇·白族院落","note":"留一晚听风，安静","price":"¥380/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"喜洲粑粑","note":"现烤趁热，甜咸各来一个","price":"¥8"},
         {"name":"大理古城·酸辣鱼","note":"下关做法，配米线吃","price":"人均 ¥60"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"洱海生态廊道","note":"骑行两天不腻，海舌段最美","price":"免费"},
         {"name":"崇圣寺三塔","note":"傍晚逆光最好看","price":"¥75"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"环海电动车","note":"古城租一天，续航看电池","price":"¥60/天"},
         {"name":"大理站到古城","note":"公交 8 路约 40 分钟","price":"¥2"}]}
     ]'::jsonb),

    -- 3 东京
    ('东京赏樱五日｜花期跑赢人潮的路线',
     'https://zh.wikipedia.org/wiki/东京都',
     '["https://picsum.photos/seed/tokyo-sakura/960/640","https://picsum.photos/seed/tokyo-sakura-2/960/640","https://picsum.photos/seed/tokyo-sakura-3/960/640","https://picsum.photos/seed/tokyo-sakura-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"上野·三井花园酒店","note":"出门就是赏樱点","price":"¥1200/晚"},
         {"name":"新宿·商务酒店","note":"交通枢纽，晚归方便","price":"¥900/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"筑地场外市场","note":"早市海鲜丼，7 点前到","price":"人均 ¥120"},
         {"name":"目黑川·立饮居酒屋","note":"赏樱顺路喝一杯","price":"人均 ¥150"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"上野公园","note":"花期最早，人也最多","price":"免费"},
         {"name":"千鸟渊","note":"护城河划船看樱","price":"船 ¥800/30分"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"都营地下铁 1 日券","note":"赏樱三条线全覆盖","price":"¥600"},
         {"name":"镰仓一日往返","note":"JR 横须贺线约 1 小时","price":"¥1000"}]}
     ]'::jsonb),

    -- 4 西安
    ('西安三日｜把十三朝走成一条线',
     'https://zh.wikipedia.org/wiki/西安市',
     '["https://picsum.photos/seed/xian-citywall/960/640","https://picsum.photos/seed/xian-citywall-2/960/640","https://picsum.photos/seed/xian-citywall-3/960/640","https://picsum.photos/seed/xian-citywall-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"钟楼·亚朵酒店","note":"城墙内，去哪都近","price":"¥480/晚"},
         {"name":"回民街旁·青旅","note":"预算友好，晚上热闹","price":"¥120/床"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"回民街·秦豫肉夹馍","note":"只排这一家就够","price":"¥15"},
         {"name":"子午路张记·葫芦头","note":"本地人早餐","price":"人均 ¥35"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"秦始皇兵马俑","note":"上午去，一定配讲解","price":"¥120"},
         {"name":"西安城墙","note":"傍晚骑行一圈约 2 小时","price":"¥54"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 2 号线","note":"钟楼到北客站约 30 分钟","price":"¥2 起"},
         {"name":"兵马俑专线","note":"火车站东广场乘游 5","price":"¥8"}]}
     ]'::jsonb),

    -- 5 厦门
    ('厦门四日｜鼓浪屿之外的厦门更好玩',
     'https://zh.wikipedia.org/wiki/厦门市',
     '["https://picsum.photos/seed/xiamen-seaside/960/640","https://picsum.photos/seed/xiamen-seaside-2/960/640","https://picsum.photos/seed/xiamen-seaside-3/960/640","https://picsum.photos/seed/xiamen-seaside-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"沙坡尾·海景公寓","note":"老渔港改造，近厦大","price":"¥390/晚"},
         {"name":"曾厝垵·民宿","note":"近环岛路，晚上略吵","price":"¥280/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"八市·海鲜加工","note":"早上自己买海鲜找店加工","price":"人均 ¥90"},
         {"name":"中山路·沙茶面","note":"本地早餐首选","price":"¥18"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"环岛路骑行","note":"曾厝垵到黄厝一段最好骑","price":"免费"},
         {"name":"沙坡尾艺术西区","note":"傍晚拍照，顺路看日落","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"BRT 快速公交","note":"跨岛比地铁快","price":"¥1 起"},
         {"name":"轮渡到鼓浪屿","note":"旺季提前网上订票","price":"¥35 往返"}]}
     ]'::jsonb),

    -- 6 重庆
    ('重庆三日｜8D 城市的正确打开方式',
     'https://zh.wikipedia.org/wiki/重庆市',
     '["https://picsum.photos/seed/chongqing-night/960/640","https://picsum.photos/seed/chongqing-night-2/960/640","https://picsum.photos/seed/chongqing-night-3/960/640","https://picsum.photos/seed/chongqing-night-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"解放碑·江景酒店","note":"看两江夜景","price":"¥450/晚"},
         {"name":"观音桥·青旅","note":"夜生活方便，预算友好","price":"¥110/床"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"珮姐老火锅","note":"微辣也能上头，早点去排号","price":"人均 ¥110"},
         {"name":"重庆小面","note":"早餐随便一家都不难吃","price":"¥12"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"洪崖洞","note":"晚上去，白天没意思","price":"免费"},
         {"name":"长江索道","note":"早去少排队","price":"¥30 单程"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"轻轨 2 号线","note":"李子坝穿楼段必坐","price":"¥2 起"},
         {"name":"南山一棵树观景台","note":"打车上去看夜景","price":"¥30"}]}
     ]'::jsonb),

    -- 7 青海
    ('青海大环线七日｜一半是湖一半是戈壁',
     'https://zh.wikipedia.org/wiki/青海省',
     '["https://picsum.photos/seed/qinghai-loop/960/640","https://picsum.photos/seed/qinghai-loop-2/960/640","https://picsum.photos/seed/qinghai-loop-3/960/640","https://picsum.photos/seed/qinghai-loop-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"青海湖畔·帐篷酒店","note":"为看日出，夜里很冷","price":"¥500/晚"},
         {"name":"大柴旦·小旅馆","note":"补给点，条件一般","price":"¥260/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"西宁·莫家街","note":"出发前把路上的吃食补齐","price":"人均 ¥50"},
         {"name":"茶卡·川菜馆","note":"盐湖回来补充碳水","price":"人均 ¥60"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"青海湖二郎剑","note":"7 月油菜花最好看","price":"¥100"},
         {"name":"茶卡盐湖","note":"晴天才有天空之镜","price":"¥60"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"自驾租车","note":"西宁取还，务必选带全险的","price":"¥400/天"},
         {"name":"敦煌段加油","note":"补给点稀，见站就加","price":"油费约 ¥0.6/km"}]}
     ]'::jsonb),

    -- 8 京都
    ('京都慢走四日｜寺庙与咖啡各一半',
     'https://zh.wikipedia.org/wiki/京都市',
     '["https://picsum.photos/seed/kyoto-temple/960/640","https://picsum.photos/seed/kyoto-temple-2/960/640","https://picsum.photos/seed/kyoto-temple-3/960/640","https://picsum.photos/seed/kyoto-temple-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"清水寺附近·町家民宿","note":"能赶上第一批进寺","price":"¥850/晚"},
         {"name":"京都站·格兰比亚酒店","note":"交通最方便","price":"¥1100/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"锦市场","note":"边走边吃，注意歇业早","price":"人均 ¥80"},
         {"name":"哲学之道·咖啡馆","note":"下午发呆用","price":"人均 ¥70"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"伏见稻荷大社","note":"傍晚人少，千本鸟居","price":"免费"},
         {"name":"清水寺","note":"赶早 6 点开门","price":"¥400"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"市巴士 1 日券","note":"寺庙线全覆盖","price":"¥700"},
         {"name":"京都到奈良","note":"近铁约 45 分钟","price":"¥640"}]}
     ]'::jsonb),

    -- 9 三亚
    ('三亚亲子五日｜带娃也能有假期感',
     'https://zh.wikipedia.org/wiki/三亚市',
     '["https://picsum.photos/seed/sanya-beach/960/640","https://picsum.photos/seed/sanya-beach-2/960/640","https://picsum.photos/seed/sanya-beach-3/960/640","https://picsum.photos/seed/sanya-beach-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"亚龙湾·亲子酒店","note":"带泳池和儿童俱乐部","price":"¥1600/晚"},
         {"name":"海棠湾·度假村","note":"房间大，含早餐","price":"¥2000/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"第一市场·海鲜加工","note":"自购加工，认准明码标价","price":"人均 ¥150"},
         {"name":"酒店自助早餐","note":"带娃首选，省事","price":"含房费"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"蜈支洲岛","note":"浮潜一天，早班船不晒","price":"¥168"},
         {"name":"亚龙湾沙滩","note":"酒店门口就能躺","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"环岛高铁","note":"凤凰机场到亚龙湾约 30 分钟","price":"¥17"},
         {"name":"酒店接驳车","note":"问前台要时刻表","price":"免费"}]}
     ]'::jsonb),

    -- 10 长沙
    ('长沙周末两日｜为了吃的也值得来一趟',
     'https://zh.wikipedia.org/wiki/长沙市',
     '["https://picsum.photos/seed/changsha-food/960/640","https://picsum.photos/seed/changsha-food-2/960/640","https://picsum.photos/seed/changsha-food-3/960/640","https://picsum.photos/seed/changsha-food-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"五一广场·酒店","note":"小吃街全在步行范围","price":"¥350/晚"},
         {"name":"橘子洲旁·民宿","note":"看江景，晚上安静","price":"¥300/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"茶颜悦色","note":"排队也值，记得少糖","price":"¥15"},
         {"name":"文和友·口味虾","note":"当宵夜吃，人均不低","price":"人均 ¥120"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"橘子洲头","note":"坐小火车到江心","price":"免费"},
         {"name":"太平老街","note":"晚上逛，边吃边走","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 1/2 号线","note":"五一广场换乘最方便","price":"¥2 起"},
         {"name":"打车跨江","note":"晚高峰堵，优先坐地铁","price":"¥25 上下"}]}
     ]'::jsonb),

    -- 11 川西
    ('川西小环线五日｜雪山在窗外',
     'https://zh.wikipedia.org/wiki/四姑娘山',
     '["https://picsum.photos/seed/western-sichuan/960/640","https://picsum.photos/seed/western-sichuan-2/960/640","https://picsum.photos/seed/western-sichuan-3/960/640","https://picsum.photos/seed/western-sichuan-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"四姑娘山·山景客栈","note":"抬头就是雪山","price":"¥420/晚"},
         {"name":"新都桥·摄影主题客栈","note":"出片点集中","price":"¥360/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"丹巴·藏式土火锅","note":"海拔高，喝点热的","price":"人均 ¥90"},
         {"name":"新都桥·川菜馆","note":"路上最稳的选择","price":"人均 ¥50"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"四姑娘山双桥沟","note":"观光车加步道，半天够","price":"¥80"},
         {"name":"新都桥","note":"晨昏光线最好","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"成都出发自驾","note":"海拔爬升快，前两天别剧烈运动","price":"¥350/天"},
         {"name":"高原氧气罐","note":"随身备两罐以防万一","price":"¥30"}]}
     ]'::jsonb),

    -- 12 青岛
    ('青岛三日｜啤酒和海风都要',
     'https://zh.wikipedia.org/wiki/青岛市',
     '["https://picsum.photos/seed/qingdao-coast/960/640","https://picsum.photos/seed/qingdao-coast-2/960/640","https://picsum.photos/seed/qingdao-coast-3/960/640","https://picsum.photos/seed/qingdao-coast-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"八大关·酒店","note":"老洋房区，安静","price":"¥520/晚"},
         {"name":"台东·民宿","note":"夜市旁边，热闹","price":"¥300/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"台东夜市·原浆啤酒","note":"塑料袋装最地道","price":"¥20"},
         {"name":"劈柴院·海鲜","note":"淡季来更划算","price":"人均 ¥100"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"栈桥","note":"清晨去，人最少","price":"免费"},
         {"name":"小麦岛","note":"看日落，别赶末班公交","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 2/3 号线","note":"覆盖沿海主要景点","price":"¥2 起"},
         {"name":"崂山景区专线","note":"市区直达景区","price":"¥20"}]}
     ]'::jsonb),

    -- 13 云南（丽江+香格里拉）
    ('丽江+香格里拉六日｜海拔缓慢爬升路线',
     'https://zh.wikipedia.org/wiki/云南省',
     '["https://picsum.photos/seed/lijiang-oldtown/960/640","https://picsum.photos/seed/lijiang-oldtown-2/960/640","https://picsum.photos/seed/lijiang-oldtown-3/960/640","https://picsum.photos/seed/lijiang-oldtown-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"丽江古城·客栈","note":"第一晚适应海拔","price":"¥320/晚"},
         {"name":"香格里拉·藏式酒店","note":"订有供氧的房间","price":"¥500/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"丽江·腊排骨火锅","note":"高原上补充热量","price":"人均 ¥80"},
         {"name":"香格里拉·牦牛肉","note":"火锅或烤肉都行","price":"人均 ¥95"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"普达措国家公园","note":"一天走一条线","price":"¥140"},
         {"name":"松赞林寺","note":"早上去，别爬太快","price":"¥105"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"丽江到香格里拉","note":"大巴约 4 小时，海拔缓慢爬升","price":"¥65"},
         {"name":"古城内步行","note":"机动车进不去","price":"免费"}]}
     ]'::jsonb),

    -- 14 首尔
    ('首尔购物四日｜清单式不走回头路',
     'https://zh.wikipedia.org/wiki/首尔',
     '["https://picsum.photos/seed/seoul-street/960/640","https://picsum.photos/seed/seoul-street-2/960/640","https://picsum.photos/seed/seoul-street-3/960/640","https://picsum.photos/seed/seoul-street-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"明洞·酒店","note":"购物动线的中心","price":"¥800/晚"},
         {"name":"弘大·民宿","note":"夜生活方便，略吵","price":"¥550/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"明洞·烤肉","note":"有单人套餐，独自来也友好","price":"人均 ¥120"},
         {"name":"广藏市场·绿豆煎饼","note":"配马格利酒吃","price":"人均 ¥70"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"景福宫","note":"穿韩服可免门票","price":"¥20"},
         {"name":"南山首尔塔","note":"傍晚上去看夜景","price":"¥120"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"T-money 交通卡","note":"地铁公交通用，押金可退","price":"押金 ¥25"},
         {"name":"机场快线 AREX","note":"仁川到首尔站约 45 分钟","price":"¥55"}]}
     ]'::jsonb),

    -- 15 桂林
    ('桂林阳朔四日｜遇龙河比漓江更安静',
     'https://zh.wikipedia.org/wiki/桂林市',
     '["https://picsum.photos/seed/guilin-river/960/640","https://picsum.photos/seed/guilin-river-2/960/640","https://picsum.photos/seed/guilin-river-3/960/640","https://picsum.photos/seed/guilin-river-4/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"阳朔西街·民宿","note":"只住不吃，选巷内更安静","price":"¥280/晚"},
         {"name":"桂林市区·江景酒店","note":"临漓江，看晨雾","price":"¥360/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"桂林米粉","note":"早餐小碗就够","price":"¥10"},
         {"name":"阳朔·啤酒鱼","note":"挑本地人多的店","price":"人均 ¥85"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"漓江精华段","note":"坐大船看全景，选四星船","price":"¥215"},
         {"name":"遇龙河竹筏","note":"慢漂半天，比漓江安静","price":"¥180"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"桂林到阳朔","note":"大巴约 1.5 小时","price":"¥35"},
         {"name":"阳朔内骑行","note":"十里画廊一路骑过去","price":"¥30/天"}]}
     ]'::jsonb)
) AS v(title, source_url, images, plan_sections)
WHERE g.title = v.title;
