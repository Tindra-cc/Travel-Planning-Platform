package com.travel.modules.guidebook.data;

import java.util.Set;

import org.springframework.util.StringUtils;

/** 中国大陆目的地判定：命中则签证源返回「无需签证」，而不是「暂无资料」。 */
public final class DomesticDestination {

    private static final Set<String> KEYWORDS = Set.of(
            "国内", "中国大陆",
            "北京", "上海", "天津", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江",
            "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南",
            "广东", "海南", "四川", "贵州", "云南", "陕西", "甘肃", "青海",
            "内蒙古", "广西", "西藏", "宁夏", "新疆",
            "成都", "西安", "广州", "深圳", "杭州", "南京", "苏州", "厦门", "青岛",
            "大连", "武汉", "长沙", "昆明", "丽江", "大理", "三亚", "海口", "桂林",
            "阳朔", "贵阳", "哈尔滨", "长春", "沈阳", "呼和浩特", "乌鲁木齐", "拉萨",
            "兰州", "西宁", "银川", "太原", "石家庄", "郑州", "合肥", "南昌", "福州",
            "泉州", "宁波", "温州", "无锡", "常州", "扬州", "珠海", "佛山", "东莞",
            "北海", "张家界", "黄山", "敦煌", "承德", "洛阳", "开封");

    private DomesticDestination() {
    }

    public static boolean isDomestic(String destination) {
        if (!StringUtils.hasText(destination)) {
            return false;
        }
        return KEYWORDS.stream().anyMatch(destination::contains);
    }
}
