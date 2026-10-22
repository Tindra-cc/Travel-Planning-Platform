package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

import com.travel.modules.chat.dto.IntentDto;

/**
 * 无 API key 时的演示数据。用于演示环境让四个数据块始终有内容，
 * 数值为区间参考并带「以下单页为准」，不冒充实时报价。
 */
public final class DemoTravelData {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private DemoTravelData() {
    }

    public static Optional<DataBlock> flight(IntentDto intent, String fetchedAt) {
        if (intent == null || !StringUtils.hasText(intent.getDestination())) {
            return Optional.empty();
        }
        String destination = intent.getDestination();
        int[] range = flightRange(destination);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", destination);
        if (StringUtils.hasText(intent.getOrigin())) {
            data.put("origin", intent.getOrigin());
        }
        data.put("currency", "CNY");
        data.put("min", range[0]);
        data.put("max", range[1]);
        data.put("provider", "demo");
        data.put("note", "演示参考价，非实时报价");
        String route = StringUtils.hasText(intent.getOrigin())
                ? intent.getOrigin() + "→" + destination : destination;
        String summary = "机票参考价 " + range[0] + "–" + range[1] + " CNY（" + route
                + "，以下单页为准）";
        return Optional.of(DataBlock.ok("flight_price", summary, data, now(fetchedAt)));
    }

    public static Optional<DataBlock> hotel(IntentDto intent, String fetchedAt) {
        if (intent == null || !StringUtils.hasText(intent.getDestination())) {
            return Optional.empty();
        }
        String destination = intent.getDestination();
        int[] range = hotelRange(destination);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", destination);
        if (intent.getDateWindow() != null) {
            data.put("checkIn", intent.getDateWindow().getStart());
            data.put("checkOut", intent.getDateWindow().getEnd());
        }
        data.put("currency", "CNY");
        data.put("min", range[0]);
        data.put("max", range[1]);
        data.put("provider", "demo");
        data.put("note", "演示参考价，非实时报价");
        String summary = "酒店参考价 " + range[0] + "–" + range[1]
                + " CNY/晚（以下单页为准）";
        return Optional.of(DataBlock.ok("hotel_price", summary, data, now(fetchedAt)));
    }

    public static Optional<DataBlock> news(IntentDto intent, String fetchedAt) {
        if (intent == null || !StringUtils.hasText(intent.getDestination())) {
            return Optional.empty();
        }
        String destination = intent.getDestination();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(article(destination, "热门景点开放时间调整，出行前请确认预约"));
        items.add(article(destination, "本周天气多变，建议随身携带雨具"));
        items.add(article(destination, "公共交通出行提示：节假日班次有调整"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", destination);
        data.put("articles", items);
        data.put("provider", "demo");
        data.put("note", "演示动态，出行前请核实");
        String summary = "「" + destination + "」近期出行提示：热门景点建议提前预约；"
                + "天气多变注意增减衣物（需核实）";
        return Optional.of(DataBlock.ok("local_news", summary, data, now(fetchedAt)));
    }

    public static DataBlock visaFallback(String destination, String fetchedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", destination);
        data.put("nationality", "CN");
        data.put("policyType", "generic");
        data.put("policySummary", "赴" + destination + "旅行通常需办理签证，具体以使领馆最新公告为准。");
        data.put("entryRequirements", "护照有效期 6 个月以上；行程单；酒店预订单；财力证明。");
        data.put("provider", "demo");
        String summary = "赴" + destination + "通常需办理签证（通用提示，以使领馆公告为准）";
        return DataBlock.ok("visa", summary, data, now(fetchedAt));
    }

    private static Map<String, Object> article(String destination, String title) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", "「" + destination + "」" + title);
        item.put("url", "");
        item.put("source", "出行提示");
        item.put("seenAt", "");
        return item;
    }

    private static int[] flightRange(String destination) {
        if (DomesticDestination.isDomestic(destination)) {
            if (containsAny(destination, "乌鲁木齐", "拉萨", "三亚", "哈尔滨", "呼和浩特")) {
                return new int[] {900, 2200};
            }
            return new int[] {500, 1400};
        }
        if (containsAny(destination, "东京", "大阪", "京都", "北海道", "札幌", "冲绳",
                "首尔", "釜山", "台北", "香港", "澳门")) {
            return new int[] {1600, 3800};
        }
        if (containsAny(destination, "曼谷", "清迈", "普吉", "新加坡", "吉隆坡", "巴厘岛",
                "胡志明", "河内", "岘港", "马尼拉", "雅加达")) {
            return new int[] {1200, 3200};
        }
        if (containsAny(destination, "迪拜", "多哈", "伊斯坦布尔")) {
            return new int[] {2800, 6000};
        }
        if (containsAny(destination, "巴黎", "伦敦", "罗马", "米兰", "巴塞罗那", "马德里",
                "阿姆斯特丹", "法兰克福", "慕尼黑", "苏黎世", "维也纳", "布拉格")) {
            return new int[] {4500, 9500};
        }
        if (containsAny(destination, "纽约", "洛杉矶", "旧金山", "西雅图", "芝加哥",
                "波士顿", "华盛顿", "温哥华", "多伦多")) {
            return new int[] {5000, 11000};
        }
        if (containsAny(destination, "悉尼", "墨尔本", "奥克兰")) {
            return new int[] {3500, 8000};
        }
        return new int[] {2500, 6000};
    }

    private static int[] hotelRange(String destination) {
        if (DomesticDestination.isDomestic(destination)) {
            if (containsAny(destination, "北京", "上海", "深圳", "广州")) {
                return new int[] {450, 1300};
            }
            return new int[] {220, 800};
        }
        if (containsAny(destination, "香港", "澳门", "东京", "大阪", "京都", "北海道",
                "札幌", "冲绳", "首尔", "新加坡", "台北")) {
            return new int[] {550, 1500};
        }
        if (containsAny(destination, "曼谷", "清迈", "普吉", "吉隆坡", "巴厘岛",
                "胡志明", "河内", "岘港", "马尼拉", "雅加达")) {
            return new int[] {200, 800};
        }
        if (containsAny(destination, "巴黎", "伦敦", "罗马", "米兰", "巴塞罗那", "马德里",
                "阿姆斯特丹", "法兰克福", "慕尼黑", "苏黎世", "维也纳", "布拉格")) {
            return new int[] {700, 2000};
        }
        if (containsAny(destination, "纽约", "洛杉矶", "旧金山", "西雅图", "芝加哥",
                "波士顿", "华盛顿", "温哥华", "多伦多")) {
            return new int[] {900, 2500};
        }
        if (containsAny(destination, "悉尼", "墨尔本", "奥克兰")) {
            return new int[] {600, 1600};
        }
        return new int[] {300, 900};
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String now(String fetchedAt) {
        return StringUtils.hasText(fetchedAt)
                ? fetchedAt : LocalDateTime.now().format(TS);
    }
}
