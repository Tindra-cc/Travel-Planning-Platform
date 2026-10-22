package com.travel.modules.guidebook.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

/**
 * 城市 → IATA 机场代码。SerpAPI google_flights 只接受代码，
 * 未收录的城市返回 empty，机票源保持 unavailable 而不是猜一个机场。
 */
public final class IataCodes {

    private static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        // 国内
        put("PEK", "北京", "北京市");
        put("SHA", "上海", "上海市");
        put("CTU", "成都", "成都市");
        put("CKG", "重庆", "重庆市");
        put("CAN", "广州", "广州市");
        put("SZX", "深圳", "深圳市");
        put("HGH", "杭州", "杭州市");
        put("NKG", "南京", "南京市");
        put("XIY", "西安", "西安市");
        put("WUH", "武汉", "武汉市");
        put("CSX", "长沙", "长沙市");
        put("XMN", "厦门", "厦门市");
        put("TAO", "青岛", "青岛市");
        put("TSN", "天津", "天津市");
        put("KMG", "昆明", "昆明市");
        put("SYX", "三亚", "三亚市");
        put("HAK", "海口", "海口市");
        put("DLC", "大连", "大连市");
        put("SHE", "沈阳", "沈阳市");
        put("HRB", "哈尔滨", "哈尔滨市");
        put("TNA", "济南", "济南市");
        put("CGO", "郑州", "郑州市");
        put("HFE", "合肥", "合肥市");
        put("FOC", "福州", "福州市");
        put("NNG", "南宁", "南宁市");
        put("KWE", "贵阳", "贵阳市");
        put("LHW", "兰州", "兰州市");
        put("URC", "乌鲁木齐", "乌鲁木齐市");
        put("LXA", "拉萨", "拉萨市");
        put("HET", "呼和浩特", "呼和浩特市");
        put("TYN", "太原", "太原市");
        put("SJW", "石家庄", "石家庄市");
        put("NGB", "宁波", "宁波市");
        put("WNZ", "温州", "温州市");
        put("ZUH", "珠海", "珠海市");
        put("WUX", "无锡", "无锡市");
        put("KWL", "桂林", "桂林市");
        // 国际/地区
        put("HKG", "香港", "香港特别行政区");
        put("MFM", "澳门", "澳门特别行政区");
        put("TPE", "台北", "台北市");
        put("TYO", "东京", "东京都", "日本东京");
        put("OSA", "大阪", "大阪市", "日本大阪");
        put("CTS", "札幌", "札幌市", "北海道");
        put("SEL", "首尔", "首尔市", "韩国首尔");
        put("PUS", "釜山", "釜山市");
        put("BKK", "曼谷", "泰国曼谷");
        put("CNX", "清迈", "清迈府");
        put("HKT", "普吉", "普吉岛");
        put("SIN", "新加坡");
        put("KUL", "吉隆坡", "马来西亚吉隆坡");
        put("DPS", "巴厘岛", "巴厘");
        put("CGK", "雅加达");
        put("MNL", "马尼拉");
        put("HAN", "河内");
        put("SGN", "胡志明", "胡志明市");
        put("DAD", "岘港");
        put("DXB", "迪拜");
        put("DOH", "多哈");
        put("IST", "伊斯坦布尔");
        put("PAR", "巴黎");
        put("LON", "伦敦");
        put("ROM", "罗马");
        put("MIL", "米兰");
        put("BCN", "巴塞罗那");
        put("MAD", "马德里");
        put("AMS", "阿姆斯特丹");
        put("FRA", "法兰克福");
        put("MUC", "慕尼黑");
        put("ZRH", "苏黎世");
        put("VIE", "维也纳");
        put("PRG", "布拉格");
        put("NYC", "纽约");
        put("LAX", "洛杉矶");
        put("SFO", "旧金山", "圣弗朗西斯科");
        put("SEA", "西雅图");
        put("CHI", "芝加哥");
        put("BOS", "波士顿");
        put("WAS", "华盛顿");
        put("YVR", "温哥华");
        put("YTO", "多伦多");
        put("SYD", "悉尼");
        put("MEL", "墨尔本");
        put("AKL", "奥克兰");
        put("CAI", "开罗");
        put("CPT", "开普敦");
        put("NBO", "内罗毕");
        put("MRU", "毛里求斯");
        put("MLE", "马尔代夫", "马累");
    }

    private IataCodes() {
    }

    public static Optional<String> resolve(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.matches("(?i)[a-z]{3}")) {
            return Optional.of(value.toUpperCase());
        }
        String normalized = value.replaceAll("[市都省县区]$", "");
        String direct = CODES.get(value);
        if (direct != null) {
            return Optional.of(direct);
        }
        direct = CODES.get(normalized);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (Map.Entry<String, String> entry : CODES.entrySet()) {
            if (value.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private static void put(String code, String... names) {
        for (String name : names) {
            CODES.put(name, code);
        }
    }
}
