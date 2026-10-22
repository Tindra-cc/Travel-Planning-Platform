package com.travel.modules.guidebook.offline;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;

/** 自包含离线 HTML：内联样式与 base64 地图，不引用任何外部静态资源。 */
@Component
public class HtmlPackRenderer {

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "activity", "活动", "meal", "餐饮", "transport", "交通",
            "stay", "住宿", "free", "自由");

    public String render(OfflinePackContent content) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(content.getTitle())).append("</title>")
                .append("<style>")
                .append("body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;")
                .append("margin:0;padding:24px;color:#1f2937;background:#f8fafc;line-height:1.6}")
                .append(".wrap{max-width:780px;margin:0 auto;background:#fff;padding:28px;border-radius:12px}")
                .append("h1{font-size:26px;margin:0 0 8px}h2{font-size:19px;margin:28px 0 10px;border-left:4px solid #3b82f6;padding-left:10px}")
                .append(".meta{color:#64748b;font-size:13px;margin-bottom:20px}")
                .append(".node{border:1px solid #e2e8f0;border-radius:8px;padding:12px 14px;margin:10px 0}")
                .append(".node-head{display:flex;justify-content:space-between;gap:12px;font-weight:600}")
                .append(".time{color:#2563eb;font-size:13px;white-space:nowrap}")
                .append(".note{color:#475569;font-size:14px;margin:6px 0 0}")
                .append(".src{color:#94a3b8;font-size:12px;margin-top:6px}")
                .append(".map{max-width:100%;border-radius:8px;margin:8px 0;border:1px solid #e2e8f0}")
                .append("table{border-collapse:collapse;width:100%;font-size:14px}th,td{border:1px solid #e2e8f0;padding:6px 10px;text-align:left}")
                .append("ul{padding-left:20px;margin:6px 0}.muted{color:#94a3b8;font-size:13px}")
                .append(".warn{background:#fef3c7;border:1px solid #fcd34d;padding:10px 12px;border-radius:8px;font-size:13px}")
                .append(".emg{background:#fff1f2;border:1px solid #fecdd3;padding:12px;border-radius:8px}")
                .append("footer{margin-top:28px;color:#94a3b8;font-size:12px}")
                .append("</style></head><body><div class=\"wrap\">");

        sb.append("<h1>").append(esc(content.getTitle())).append("</h1>");
        appendMeta(sb, content.getIntent(), content.getVersion());

        if (content.isMapUnavailable()) {
            sb.append("<p class=\"warn\">部分日期地图截图暂不可用，已保留地点与坐标，请联网后核实。</p>");
        }

        List<OfflinePackContent.DayContent> days = content.getDays();
        if (days != null) {
            for (OfflinePackContent.DayContent day : days) {
                sb.append("<h2>第 ").append(day.getDay() == null ? "" : day.getDay())
                        .append(" 天</h2>");
                appendMap(sb, day);
                if (day.getNodes() == null || day.getNodes().isEmpty()) {
                    sb.append("<p class=\"muted\">本日暂无安排</p>");
                } else {
                    for (OfflinePackContent.NodeContent node : day.getNodes()) {
                        appendNode(sb, node);
                    }
                }
            }
        }

        appendBudget(sb, content.getBudget());
        appendTips(sb, "行前准备", content.getPackingList());
        appendTips(sb, "文化贴士", content.getCulturalTips());
        appendTips(sb, "避坑提示", content.getPitfallTips());
        appendEmergency(sb, content);

        if (StringUtils.hasText(content.getDisclaimer())) {
            sb.append("<footer>").append(esc(content.getDisclaimer())).append("</footer>");
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private void appendMeta(StringBuilder sb, IntentDto intent, Integer version) {
        sb.append("<div class=\"meta\">");
        if (intent != null) {
            if (StringUtils.hasText(intent.getDestination())) {
                sb.append("目的地：").append(esc(intent.getDestination())).append(" · ");
            }
            if (intent.getDateWindow() != null) {
                sb.append(esc(intent.getDateWindow().getStart())).append(" ~ ")
                        .append(esc(intent.getDateWindow().getEnd())).append(" · ");
            }
            if (intent.getParty() != null && intent.getParty().getAdults() != null) {
                sb.append("成人 ").append(intent.getParty().getAdults()).append(" 人 · ");
            }
            if (intent.getBudget() != null) {
                sb.append("预算 ").append(esc(String.valueOf(intent.getBudget().getAmount())))
                        .append(' ').append(esc(intent.getBudget().getCurrency())).append(" · ");
            }
        }
        sb.append("版本 v").append(version == null ? "?" : version).append("</div>");
    }

    private void appendMap(StringBuilder sb, OfflinePackContent.DayContent day) {
        if (day.getMapImage() == null) {
            return;
        }
        String type = day.getMapImageType() == null ? "image/png" : day.getMapImageType();
        sb.append("<img class=\"map\" alt=\"第 ").append(day.getDay()).append(" 天路线示意图\" src=\"data:")
                .append(type).append(";base64,")
                .append(Base64.getEncoder().encodeToString(day.getMapImage()))
                .append("\">");
    }

    private void appendNode(StringBuilder sb, OfflinePackContent.NodeContent node) {
        sb.append("<div class=\"node\"><div class=\"node-head\"><span>")
                .append(esc(defaultText(node.getPlaceName(), "待定地点")))
                .append("</span><span class=\"time\">")
                .append(esc(timeRange(node))).append("</span></div>");
        sb.append("<div class=\"muted\">").append(esc(typeLabel(node.getType())));
        if (StringUtils.hasText(node.getOpeningHours())) {
            sb.append(" · 营业时间 ").append(esc(node.getOpeningHours()));
        }
        sb.append("</div>");
        if (StringUtils.hasText(node.getNote())) {
            sb.append("<p class=\"note\">").append(esc(node.getNote())).append("</p>");
        }
        if (node.getLat() != null && node.getLng() != null) {
            sb.append("<p class=\"src\">坐标 ").append(String.format("%.5f, %.5f", node.getLat(), node.getLng()))
                    .append("</p>");
        }
        sb.append("</div>");
    }

    private void appendBudget(StringBuilder sb, BudgetBreakdownDto budget) {
        if (budget == null || budget.getItems() == null || budget.getItems().isEmpty()) {
            return;
        }
        sb.append("<h2>预算拆解</h2><table><thead><tr><th>类别</th><th>最低</th><th>最高</th></tr></thead><tbody>");
        for (BudgetBreakdownDto.Item item : budget.getItems()) {
            sb.append("<tr><td>").append(esc(item.getCategory())).append("</td><td>")
                    .append(item.getMin()).append("</td><td>").append(item.getMax()).append("</td></tr>");
        }
        if (budget.getTotal() != null) {
            sb.append("<tr><td><strong>合计</strong></td><td>").append(budget.getTotal().getMin())
                    .append("</td><td>").append(budget.getTotal().getMax()).append("</td></tr>");
        }
        sb.append("</tbody></table>");
        if (StringUtils.hasText(budget.getCurrency())) {
            sb.append("<p class=\"muted\">币种：").append(esc(budget.getCurrency())).append("</p>");
        }
    }

    private void appendTips(StringBuilder sb, String title, List<String> tips) {
        if (tips == null || tips.isEmpty()) {
            return;
        }
        sb.append("<h2>").append(title).append("</h2><ul>");
        for (String tip : tips) {
            sb.append("<li>").append(esc(tip)).append("</li>");
        }
        sb.append("</ul>");
    }

    private void appendEmergency(StringBuilder sb, OfflinePackContent content) {
        sb.append("<h2>紧急信息</h2><div class=\"emg\"><ul>");
        if (content.getEmergencyContacts() != null) {
            for (OfflinePackContent.Contact contact : content.getEmergencyContacts()) {
                sb.append("<li>").append(esc(contact.getLabel())).append("：")
                        .append(esc(contact.getNumber())).append("</li>");
            }
        }
        sb.append("</ul>");
        if (StringUtils.hasText(content.getEmergencyNote())) {
            sb.append("<p class=\"muted\">").append(esc(content.getEmergencyNote())).append("</p>");
        }
        sb.append("</div>");
    }

    private String timeRange(OfflinePackContent.NodeContent node) {
        if (!StringUtils.hasText(node.getStart()) && !StringUtils.hasText(node.getEnd())) {
            return "时间待定";
        }
        return defaultText(node.getStart(), "?") + "-" + defaultText(node.getEnd(), "?");
    }

    private String typeLabel(String type) {
        return TYPE_LABELS.getOrDefault(type, type == null ? "" : type);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
