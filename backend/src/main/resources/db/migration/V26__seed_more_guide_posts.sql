-- V26: 补充推荐攻略演示数据（9 条，覆盖国内外热门目的地）。
-- 纯演示数据：图片为 picsum 占位，价格为量级参考，出行前以实际为准。
-- source_url 留空：详情页不再展示「查看原文」。

INSERT INTO t_guide_post
    (title, destination, cover_url, summary, author_name, tags, like_count, view_count,
     duration_days, budget_level, sort_weight, source_url, images, plan_sections)
VALUES
    ('上海两日｜梧桐区压马路与江景夜色',
     '上海',
     'https://picsum.photos/seed/shanghai-plane/640/420',
     '武康路—安福路—愚园路串成一条散步线，傍晚去北外滩看陆家嘴亮灯。',
     '弄堂阿青', '["城市","人文","周末"]'::jsonb, 1422, 23890, 2, 'balanced', 70, NULL,
     '["https://picsum.photos/seed/shanghai-plane/960/640","https://picsum.photos/seed/shanghai-plane-2/960/640","https://picsum.photos/seed/shanghai-plane-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"静安寺·亚朵","note":"2/7 号线换乘方便，晚上回酒店也热闹","price":"¥520/晚"},
         {"name":"北外滩·全季","note":"江景房性价比高，步行到滨江","price":"¥460/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"老吉士酒家","note":"本帮菜，红烧肉和油爆虾必点","price":"人均 ¥150"},
         {"name":"大壶春","note":"生煎配咖喱牛肉汤，早餐刚好","price":"人均 ¥30"}]},
       {"type":"spot","title":"逛什么","items":[
         {"name":"武康路—安福路","note":"下午光线最好，咖啡馆密度极高","price":"免费"},
         {"name":"北外滩滨江","note":"19:00 后对岸亮灯，人比外滩少","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 2/10 号线","note":"虹桥机场 2 号线直达市区约 50 分钟","price":"¥3 起"},
         {"name":"轮渡","note":"金陵东路—东昌路，2 元看江景","price":"¥2"}]}
     ]'::jsonb),

    ('北京四日｜把中轴线从南走到北',
     '北京',
     'https://picsum.photos/seed/beijing-axis/640/420',
     '永定门—天坛—前门—故宫—景山—钟鼓楼，四天走完一条中轴线。',
     '胡同老赵', '["历史","人文","城市"]'::jsonb, 1876, 31204, 4, 'balanced', 68, NULL,
     '["https://picsum.photos/seed/beijing-axis/960/640","https://picsum.photos/seed/beijing-axis-2/960/640","https://picsum.photos/seed/beijing-axis-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"前门·胡同民宿","note":"步行到天安门，早起看升旗不用打车","price":"¥480/晚"},
         {"name":"东四·桔子水晶","note":"5 号线沿线，去雍和宫方便","price":"¥560/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"四季民福（故宫店）","note":"烤鸭排队久，错峰 14:00 去","price":"人均 ¥160"},
         {"name":"姚记炒肝","note":"炒肝配包子，鼓楼附近的老店","price":"人均 ¥30"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"故宫博物院","note":"提前 7 天预约，从午门进、神武门出","price":"¥60"},
         {"name":"天坛公园","note":"早上看本地人晨练，回音壁另收费","price":"¥15"},
         {"name":"景山万春亭","note":"俯拍故宫全景的最佳机位","price":"¥2"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 1/2/8 号线","note":"核心景点基本覆盖，避开早晚高峰","price":"¥3 起"},
         {"name":"共享单车","note":"胡同一带骑车比打车快","price":"¥1.5 起"}]}
     ]'::jsonb),

    ('杭州三日｜西湖之外去龙井和良渚',
     '杭州',
     'https://picsum.photos/seed/hangzhou-tea/640/420',
     '第一天环湖，第二天龙井村喝茶走九溪，第三天良渚看五千年前的城。',
     '西湖边的小满', '["自然风光","人文","松弛"]'::jsonb, 964, 16733, 3, 'economy', 66, NULL,
     '["https://picsum.photos/seed/hangzhou-tea/960/640","https://picsum.photos/seed/hangzhou-tea-2/960/640","https://picsum.photos/seed/hangzhou-tea-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"龙井村·茶园民宿","note":"推窗就是茶山，早上安静","price":"¥380/晚"},
         {"name":"湖滨·全季","note":"离断桥近，环湖方便","price":"¥450/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"绿茶餐厅（龙井路）","note":"面包诱惑和茶香鸡，早点去不排队","price":"人均 ¥90"},
         {"name":"方老大面馆","note":"片儿川，本地人早餐","price":"人均 ¥25"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"西湖环湖","note":"租车骑行约 3 小时，苏堤白堤都经过","price":"免费"},
         {"name":"九溪十八涧","note":"龙井村往下走到九溪，夏天最舒服","price":"免费"},
         {"name":"良渚古城遗址公园","note":"建议坐观光车，园区很大","price":"¥60"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 + 公交","note":"景区周边限行，公共交通更稳","price":"¥2 起"},
         {"name":"萧山机场大巴","note":"到武林门约 50 分钟","price":"¥20"}]}
     ]'::jsonb),

    ('张家界四日｜天门山与森林公园的取舍',
     '张家界',
     'https://picsum.photos/seed/zhangjiajie-peak/640/420',
     '森林公园两天走袁家界和天子山，天门山单独一天，玻璃栈道看天气。',
     '山里的阿峰', '["自然风光","徒步","摄影"]'::jsonb, 1320, 22618, 4, 'balanced', 64, NULL,
     '["https://picsum.photos/seed/zhangjiajie-peak/960/640","https://picsum.photos/seed/zhangjiajie-peak-2/960/640","https://picsum.photos/seed/zhangjiajie-peak-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"武陵源·客栈","note":"离森林公园门票站近，早上第一批进山","price":"¥260/晚"},
         {"name":"市区·天门山索道旁","note":"最后一天住市区，赶车方便","price":"¥300/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"三下锅","note":"本地土家菜，腊肉和肥肠一起炖","price":"人均 ¥60"},
         {"name":"溪布街小吃","note":"晚上逛吃，米豆腐和蒿子粑粑","price":"人均 ¥40"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"张家界国家森林公园","note":"门票 4 日有效，袁家界百龙天梯另收费","price":"¥228"},
         {"name":"天门山","note":"索道上山，玻璃栈道遇雨会关闭","price":"¥278"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"景区环保车","note":"门票含，站点之间免费换乘","price":"含在门票"},
         {"name":"高铁张家界西站","note":"到武陵源约 40 分钟车程","price":"打车 ¥80"}]}
     ]'::jsonb),

    ('哈尔滨三日｜冬天的中央大街会发光',
     '哈尔滨',
     'https://picsum.photos/seed/harbin-ice/640/420',
     '中央大街—索菲亚—防洪纪念塔一条线，晚上冰雪大世界，第二天去老道外。',
     '冰城小北', '["城市","美食","冬季"]'::jsonb, 1105, 18942, 3, 'economy', 62, NULL,
     '["https://picsum.photos/seed/harbin-ice/960/640","https://picsum.photos/seed/harbin-ice-2/960/640","https://picsum.photos/seed/harbin-ice-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"中央大街·马迭尔附近","note":"出门就是步行街，晚上逛冰雪更方便","price":"¥420/晚"},
         {"name":"哈西站·全季","note":"赶高铁方便，价格更低","price":"¥300/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"老厨家","note":"锅包肉发源店，分量大","price":"人均 ¥80"},
         {"name":"华梅西餐厅","note":"俄式西餐，红菜汤和面包","price":"人均 ¥120"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"冰雪大世界","note":"16:00 后入园看亮灯，穿最厚的衣服","price":"¥328"},
         {"name":"圣索菲亚教堂","note":"白天拍建筑，晚上有灯光","price":"¥20"},
         {"name":"老道外中华巴洛克","note":"老街区，人少适合扫街","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 2 号线","note":"中央大街站直达，过江去太阳岛","price":"¥2 起"},
         {"name":"太平机场大巴","note":"到中央大街约 50 分钟","price":"¥20"}]}
     ]'::jsonb),

    ('曼谷四日｜寺庙、夜市和马杀鸡',
     '曼谷',
     'https://picsum.photos/seed/bangkok-temple/640/420',
     '大皇宫赶早、卧佛寺按摩、郑王庙看日落，晚上去拉差达火车夜市。',
     '背包客阿南', '["美食","人文","夜生活"]'::jsonb, 1533, 26477, 4, 'budget', 60, NULL,
     '["https://picsum.photos/seed/bangkok-temple/960/640","https://picsum.photos/seed/bangkok-temple-2/960/640","https://picsum.photos/seed/bangkok-temple-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"暹罗·宜必思","note":"BTS 换乘中心，去机场和市区都方便","price":"¥320/晚"},
         {"name":"湄南河畔·民宿","note":"看河景，坐船去大皇宫","price":"¥380/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"Jay Fai","note":"米其林街头蟹肉煎蛋，排队 2 小时起","price":"人均 ¥200"},
         {"name":"水门海南鸡饭","note":"本地人午餐，便宜好吃","price":"人均 ¥25"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"大皇宫 + 玉佛寺","note":"8:30 开门就进，着装要过膝","price":"¥100"},
         {"name":"郑王庙","note":"坐渡船过去，傍晚光线最好","price":"¥20"},
         {"name":"拉差达火车夜市","note":"18:00 后出摊，先逛再吃","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"BTS + MRT","note":"避开早晚高峰，打车容易堵","price":"¥3 起"},
         {"name":"湄南河快船","note":"橙旗船最便宜，沿线景点多","price":"¥3"}]}
     ]'::jsonb),

    ('新加坡三日｜花园城市的高效玩法',
     '新加坡',
     'https://picsum.photos/seed/singapore-garden/640/420',
     '滨海湾—鱼尾狮—牛车水—小印度，第二天圣淘沙，第三天植物园收尾。',
     '狮城小周', '["城市","亲子","美食"]'::jsonb, 842, 14390, 3, 'comfort', 58, NULL,
     '["https://picsum.photos/seed/singapore-garden/960/640","https://picsum.photos/seed/singapore-garden-2/960/640","https://picsum.photos/seed/singapore-garden-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"滨海湾·酒店","note":"看金沙灯光秀，步行到鱼尾狮","price":"¥900/晚"},
         {"name":"牛车水·民宿","note":"地铁换乘方便，吃饭选择多","price":"¥450/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"麦士威熟食中心","note":"天天海南鸡饭，11:00 前去","price":"人均 ¥35"},
         {"name":"松发肉骨茶","note":"胡椒汤底，配油条","price":"人均 ¥60"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"滨海湾花园","note":"19:45 有灯光秀，云雾林值得进","price":"¥160"},
         {"name":"圣淘沙","note":"环球影城单独一天，海滩免费","price":"¥260 起"},
         {"name":"新加坡植物园","note":"早上凉爽，胡姬花园另收费","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"MRT","note":"办张交通卡，比单程票便宜","price":"¥5 起"},
         {"name":"樟宜机场","note":"地铁东西线直达市区约 40 分钟","price":"¥8"}]}
     ]'::jsonb),

    ('巴黎五日｜第一次去巴黎的取舍',
     '巴黎',
     'https://picsum.photos/seed/paris-eiffel/640/420',
     '卢浮宫和奥赛各半天，铁塔登顶看夜景，凡尔赛单独一天，剩下时间留给左岸。',
     '左岸的阿May', '["人文","艺术","城市"]'::jsonb, 2210, 38760, 5, 'comfort', 56, NULL,
     '["https://picsum.photos/seed/paris-eiffel/960/640","https://picsum.photos/seed/paris-eiffel-2/960/640","https://picsum.photos/seed/paris-eiffel-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"左岸·拉丁区","note":"步行到圣母院，书店和咖啡馆密集","price":"¥1100/晚"},
         {"name":"玛黑区·公寓","note":"地铁 1 号线沿线，晚上安全","price":"¥950/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"街角面包店","note":"牛角包 + 咖啡，早餐 €6 左右","price":"人均 ¥50"},
         {"name":"Bouillon Chartier","note":"百年平价食堂，排队但翻台快","price":"人均 ¥180"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"卢浮宫","note":"提前预约，先看三大件再逛别的","price":"¥170"},
         {"name":"埃菲尔铁塔","note":"登顶票提前买，日落前上去","price":"¥220"},
         {"name":"凡尔赛宫","note":"RER C 直达，周一闭馆","price":"¥160"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 + RER","note":"买 Navigo 周票更划算","price":"¥180/周"},
         {"name":"戴高乐机场","note":"RER B 到市区约 40 分钟","price":"¥90"}]}
     ]'::jsonb),

    ('纽约四日｜曼哈顿步行动线',
     '纽约',
     'https://picsum.photos/seed/nyc-street/640/420',
     '中央公园—大都会—时代广场—高线公园—自由女神，按上城到下城走。',
     '纽约客大刘', '["城市","艺术","购物"]'::jsonb, 1750, 30125, 4, 'luxury', 54, NULL,
     '["https://picsum.photos/seed/nyc-street/960/640","https://picsum.photos/seed/nyc-street-2/960/640","https://picsum.photos/seed/nyc-street-3/960/640"]'::jsonb,
     '[
       {"type":"stay","title":"住宿","items":[
         {"name":"中城·酒店","note":"时代广场步行可达，地铁线多","price":"¥1600/晚"},
         {"name":"布鲁克林·公寓","note":"性价比高，地铁进城 20 分钟","price":"¥1000/晚"}]},
       {"type":"food","title":"吃饭","items":[
         {"name":"Katz''s Delicatessen","note":"熏牛肉三明治，两人分一份","price":"人均 ¥180"},
         {"name":"Chelsea Market","note":"海鲜和甜品集中，逛完高线公园来","price":"人均 ¥150"}]},
       {"type":"spot","title":"景点","items":[
         {"name":"大都会艺术博物馆","note":"至少留半天，成人票约 $30","price":"¥220"},
         {"name":"自由女神像","note":"渡轮提前订，登基座要更早","price":"¥180"},
         {"name":"高线公园","note":"傍晚从南往北走，看切尔西日落","price":"免费"}]},
       {"type":"transport","title":"交通","items":[
         {"name":"地铁 OMNY","note":"刷手机进站，单程 $2.9","price":"¥21"},
         {"name":"肯尼迪机场","note":"AirTrain + 地铁约 60 分钟","price":"¥80"}]}
     ]'::jsonb);
